/**
 * CF Email Worker — ZyntaPOS Inbound Email Handler (TODO-008a)
 *
 * Routes inbound @zyntapos.com emails:
 *   - support@, billing@, bugs@, alerts@ → parse + HMAC-sign → POST /internal/email/inbound
 *   - *@zyntapos.com (staff)             → deliver to Stalwart via JMAP API
 *
 * Deploy via: Cloudflare Dashboard → Email Routing → Email Workers
 *
 * Required Worker Secrets (set via: wrangler secret put <NAME>):
 *   INBOUND_HMAC_SECRET       — matches INBOUND_EMAIL_HMAC_SECRET env var on VPS backend
 *   ZYNTA_API_ENDPOINT        — "https://api.zyntapos.com"
 *   STALWART_JMAP_URL         — "https://mail.zyntapos.com"
 *   STALWART_ADMIN_PASSWORD   — Stalwart admin password (same as STALWART_ADMIN_PASSWORD on VPS)
 */

export interface Env {
  INBOUND_HMAC_SECRET: string
  ZYNTA_API_ENDPOINT: string
  STALWART_JMAP_URL: string
  STALWART_ADMIN_PASSWORD: string
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
      // Read raw email once and keep the ArrayBuffer for potential Stalwart fallback
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
        // On API failure: deliver to Stalwart so email is never silently dropped
        console.error(`[email-inbound-handler] API returned ${resp.status} for ${to} — delivering to Stalwart`)
        await deliverToStalwartFromBuffer(rawEmailBuf, to, env)
        throw new Error(`ZyntaPOS API returned ${resp.status}: ${await resp.text()}`)
      }

      console.log(`[email-inbound-handler] Processed inbound email to ${to} from ${message.from}`)
    } else {
      // ── Staff mailbox (name@zyntapos.com) → deliver to Stalwart via JMAP ──
      const rawEmailBuf = await new Response(message.raw).arrayBuffer()
      await deliverToStalwartFromBuffer(rawEmailBuf, to, env)
      console.log(`[email-inbound-handler] Delivered email to Stalwart: ${to} from ${message.from}`)
    }
  },
}

// ── Stalwart JMAP delivery ──────────────────────────────────────────────────

/**
 * Delivers an email to Stalwart via the JMAP API using a pre-read raw email buffer.
 * 1. Look up the recipient's Stalwart account ID
 * 2. Upload the raw email blob
 * 3. Query for the Inbox mailbox ID
 * 4. Import the email into the Inbox
 */
async function deliverToStalwartFromBuffer(
  rawEmail: ArrayBuffer,
  toAddress: string,
  env: Env,
): Promise<void> {
  const username = toAddress.split("@")[0]
  const authHeader = `Basic ${btoa(`admin:${env.STALWART_ADMIN_PASSWORD}`)}`

  // Step 1: Look up the recipient's account ID in Stalwart
  const userResp = await fetch(`${env.STALWART_JMAP_URL}/api/principal/${username}`, {
    headers: { Authorization: authHeader },
  })
  if (!userResp.ok) {
    throw new Error(`Stalwart user lookup failed for '${username}': HTTP ${userResp.status}`)
  }
  const userData = (await userResp.json()) as StalwartPrincipalResponse
  const accountId = String(userData.data.id)

  // Step 2: Upload the raw RFC 5322 email blob
  const uploadResp = await fetch(`${env.STALWART_JMAP_URL}/jmap/upload/${accountId}`, {
    method: "POST",
    headers: {
      Authorization: authHeader,
      "Content-Type": "message/rfc822",
    },
    body: rawEmail,
  })
  if (!uploadResp.ok) {
    throw new Error(`Stalwart JMAP upload failed: HTTP ${uploadResp.status}`)
  }
  const uploadData = (await uploadResp.json()) as JmapUploadResponse
  const blobId = uploadData.blobId

  // Step 3: Query the Inbox mailbox ID
  const queryResp = await fetch(`${env.STALWART_JMAP_URL}/jmap`, {
    method: "POST",
    headers: {
      Authorization: authHeader,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      using: ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      methodCalls: [["Mailbox/query", { accountId, filter: { role: "inbox" } }, "0"]],
    }),
  })
  if (!queryResp.ok) {
    throw new Error(`Stalwart JMAP Mailbox/query failed: HTTP ${queryResp.status}`)
  }
  const queryData = (await queryResp.json()) as JmapResponse
  const queryResult = queryData.methodResponses[0]
  if (queryResult[0] === "error") {
    throw new Error(`JMAP Mailbox/query error: ${JSON.stringify(queryResult[1])}`)
  }
  const inboxId = (queryResult[1] as Record<string, unknown>).ids as string[]
  if (!inboxId || inboxId.length === 0) {
    throw new Error(`No Inbox mailbox found for account ${accountId}`)
  }

  // Step 4: Import the email into the Inbox
  const importResp = await fetch(`${env.STALWART_JMAP_URL}/jmap`, {
    method: "POST",
    headers: {
      Authorization: authHeader,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      using: ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
      methodCalls: [
        [
          "Email/import",
          {
            accountId,
            emails: {
              email1: {
                blobId,
                mailboxIds: { [inboxId[0]]: true },
                keywords: {},
              },
            },
          },
          "0",
        ],
      ],
    }),
  })
  if (!importResp.ok) {
    throw new Error(`Stalwart JMAP Email/import failed: HTTP ${importResp.status}`)
  }
  const importData = (await importResp.json()) as JmapResponse
  if (importData.methodResponses[0][0] === "error") {
    throw new Error(`JMAP Email/import error: ${JSON.stringify(importData.methodResponses[0][1])}`)
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

interface StalwartPrincipalResponse {
  data: {
    id: number
    type: string
    name: string
    emails: string[]
  }
}

interface JmapUploadResponse {
  accountId: string
  blobId: string
  type: string
  size: number
}

interface JmapResponse {
  methodResponses: [string, Record<string, unknown>, string][]
  sessionState: string
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
