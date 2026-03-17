/**
 * CF Email Worker — ZyntaPOS Inbound Email Handler (TODO-008a)
 *
 * Routes inbound @zyntapos.com emails:
 *   - support@, billing@, bugs@, alerts@ → parse + HMAC-sign → POST /internal/email/inbound
 *   - *@zyntapos.com (staff)             → deliver to Stalwart via HTTP relay
 *
 * Deploy via: cf-email-fix.yml workflow (action: deploy-worker or full-fix)
 *
 * Required Worker Secrets (set via: wrangler secret put <NAME>):
 *   INBOUND_HMAC_SECRET       — matches INBOUND_EMAIL_HMAC_SECRET env var on VPS backend
 *   ZYNTA_API_ENDPOINT        — "https://api.zyntapos.com"
 *   EMAIL_RELAY_SECRET        — shared secret for HTTP relay auth (matches EMAIL_RELAY_SECRET on VPS)
 *
 * Staff email delivery uses an HTTP-to-SMTP relay on the VPS because:
 *   - Stalwart's JMAP Email/import fails with admin auth (serverUnavailable)
 *   - VPS port 25 is blocked from external access (Contabo firewall)
 *   - The relay accepts email via HTTPS (port 443, Caddy) and delivers via local SMTP
 */

export interface Env {
  INBOUND_HMAC_SECRET: string
  ZYNTA_API_ENDPOINT: string
  EMAIL_RELAY_SECRET: string
  /** @deprecated No longer used for delivery */
  STALWART_JMAP_URL?: string
  /** @deprecated No longer used for delivery */
  STALWART_ADMIN_PASSWORD?: string
}

/**
 * HTTP relay endpoint on the VPS, accessible via Caddy (HTTPS, port 443).
 * Caddy routes /relay/* to the email-relay container (port 8025).
 */
const EMAIL_RELAY_URL = "https://mail.zyntapos.com/relay/email"

/** Addresses that trigger ticket creation in the ZyntaPOS API. */
const SUPPORT_INBOXES = [
  "support@zyntapos.com",
  "billing@zyntapos.com",
  "bugs@zyntapos.com",
  "alerts@zyntapos.com",
]

export default {
  async email(message: ForwardableEmailMessage, env: Env, ctx: ExecutionContext): Promise<void> {
    const to = message.to.toLowerCase()

    if (SUPPORT_INBOXES.includes(to)) {
      // ── Support inbox → parse + POST to ZyntaPOS API ─────────────────────
      const rawEmailBuf = await new Response(message.raw).arrayBuffer()
      const rawBody = new TextDecoder().decode(rawEmailBuf)

      const payload: InboundEmailPayload = {
        messageId: extractHeader(rawBody, "Message-ID"),
        inReplyTo: extractHeader(rawBody, "In-Reply-To"),
        references: extractHeader(rawBody, "References"),
        fromAddress: message.from.toLowerCase(),
        fromName: parseFromName(message.headers.get("From") ?? ""),
        toAddress: to,
        subject: message.headers.get("Subject") ?? "(no subject)",
        bodyText: extractTextBody(rawBody),
        bodyHtml: extractHtmlBody(rawBody),
        receivedAt: new Date().toISOString(),
      }

      const payloadStr = JSON.stringify(payload)
      const signature = await hmacSha256(payloadStr, env.INBOUND_HMAC_SECRET)

      const resp = await fetch(`${env.ZYNTA_API_ENDPOINT}/internal/email/inbound`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `HMAC-SHA256 ${signature}`,
        },
        body: payloadStr,
      })

      if (!resp.ok) {
        // On API failure: deliver to Stalwart via relay so email is never silently dropped
        console.error(`[email-inbound-handler] API returned ${resp.status} for ${to} — relaying to Stalwart`)
        await deliverViaRelay(rawEmailBuf, to, message.from, env)
        throw new Error(`ZyntaPOS API returned ${resp.status}: ${await resp.text()}`)
      }

      console.log(`[email-inbound-handler] Processed inbound email to ${to} from ${message.from}`)
    } else {
      // ── Staff mailbox (name@zyntapos.com) → deliver via HTTP relay to Stalwart ──
      const rawEmailBuf = await new Response(message.raw).arrayBuffer()
      await deliverViaRelay(rawEmailBuf, to, message.from, env)
      console.log(`[email-inbound-handler] Relayed email to Stalwart: ${to} from ${message.from}`)
    }
  },
}

// ── HTTP relay delivery ─────────────────────────────────────────────────────

/**
 * Delivers raw email to Stalwart via the HTTP-to-SMTP relay on the VPS.
 * POST https://mail.zyntapos.com/relay/email with raw RFC5322 body.
 */
async function deliverViaRelay(
  rawEmail: ArrayBuffer,
  recipient: string,
  sender: string,
  env: Env,
): Promise<void> {
  const resp = await fetch(EMAIL_RELAY_URL, {
    method: "POST",
    headers: {
      "Content-Type": "message/rfc822",
      "X-Relay-Secret": env.EMAIL_RELAY_SECRET,
      "X-Recipient": recipient,
      "X-Sender": sender,
    },
    body: rawEmail,
  })

  if (!resp.ok) {
    const body = await resp.text()
    throw new Error(`Email relay failed (${resp.status}): ${body}`)
  }
}

// ── Types ──────────────────────────────────────────────────────────────────────

interface InboundEmailPayload {
  messageId: string | null
  inReplyTo: string | null
  references: string | null
  fromAddress: string
  fromName: string | null
  toAddress: string
  subject: string
  bodyText: string | null
  bodyHtml: string | null
  receivedAt: string
}

// ── HMAC-SHA256 signature ──────────────────────────────────────────────────────

async function hmacSha256(payload: string, secret: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  )
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(payload))
  return btoa(String.fromCharCode(...new Uint8Array(sig)))
}

// ── Email parsing helpers ──────────────────────────────────────────────────────

function extractHeader(raw: string, name: string): string | null {
  const match = new RegExp(`^${name}:\\s*(.+)$`, "mi").exec(raw)
  return match ? match[1].trim() : null
}

function parseFromName(fromHeader: string): string | null {
  if (!fromHeader) return null
  // Formats: "John Doe <john@example.com>" OR "john@example.com"
  const match = /^"?([^"<]+)"?\s*</.exec(fromHeader.trim())
  return match ? match[1].trim() || null : null
}

function extractTextBody(raw: string): string | null {
  // Extract text/plain content from MIME message (handles both single-part and multipart)
  const match =
    /Content-Type:\s*text\/plain[^\r\n]*\r?\n(?:[^\r\n]+:\s*[^\r\n]+\r?\n)*\r?\n([\s\S]*?)(?=\r?\n--|\r?\n$|$)/i.exec(
      raw,
    )
  return match ? match[1].trim() || null : null
}

function extractHtmlBody(raw: string): string | null {
  const match =
    /Content-Type:\s*text\/html[^\r\n]*\r?\n(?:[^\r\n]+:\s*[^\r\n]+\r?\n)*\r?\n([\s\S]*?)(?=\r?\n--|\r?\n$|$)/i.exec(
      raw,
    )
  return match ? match[1].trim() || null : null
}
