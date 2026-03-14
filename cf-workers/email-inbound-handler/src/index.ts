/**
 * CF Email Worker — ZyntaPOS Inbound Email Handler (TODO-008a)
 *
 * Routes inbound @zyntapos.com emails:
 *   - support@, billing@, bugs@, alerts@ → parse + HMAC-sign → POST /internal/email/inbound
 *   - *@zyntapos.com (staff)             → forward to Stalwart IMAP via CF Email Routing
 *
 * Deploy via: Cloudflare Dashboard → Email Routing → Email Workers
 *
 * Required Worker Secrets (set via: wrangler secret put <NAME>):
 *   INBOUND_HMAC_SECRET   — matches INBOUND_EMAIL_HMAC_SECRET env var on VPS backend
 *   ZYNTA_API_ENDPOINT    — "https://api.zyntapos.com"
 */

export interface Env {
  INBOUND_HMAC_SECRET: string
  ZYNTA_API_ENDPOINT: string
}

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
      const rawBody = await new Response(message.raw).text()

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
        // On API failure: forward to postmaster so email is never silently dropped
        console.error(`[email-inbound-handler] API returned ${resp.status} for ${to} — forwarding to postmaster`)
        await message.forward("postmaster@zyntapos.com")
        throw new Error(`ZyntaPOS API returned ${resp.status}: ${await resp.text()}`)
      }

      console.log(`[email-inbound-handler] Processed inbound email to ${to} from ${message.from}`)
    } else {
      // ── Staff mailbox (name@zyntapos.com) → forward to Stalwart IMAP ─────
      // CF Email Routing delivers the email to Stalwart's SMTP (custom destination).
      // This branch handles the catch-all wildcard: *@zyntapos.com
      await message.forward(to)
    }
  },
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
