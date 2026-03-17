"""
HTTP-to-SMTP Email Relay for ZyntaPOS

Accepts raw RFC5322 email via HTTP POST and delivers via local SMTP to Stalwart.
Used by the CF Email Worker when port 25 is blocked from external access.

Endpoint: POST /relay/email
Headers:
  X-Relay-Secret: <shared HMAC secret for authentication>
  X-Recipient: <envelope recipient email address>
  X-Sender: <envelope sender email address>
  Content-Type: message/rfc822
Body: raw RFC5322 email

Returns: 200 OK on success, 4xx/5xx on error
"""

import hashlib
import hmac
import os
import smtplib
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler

RELAY_SECRET = os.environ.get("RELAY_SECRET", "")
STALWART_HOST = os.environ.get("STALWART_HOST", "stalwart")
STALWART_PORT = int(os.environ.get("STALWART_PORT", "25"))
LISTEN_PORT = int(os.environ.get("LISTEN_PORT", "8025"))


class RelayHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/relay/email":
            self.send_error(404, "Not found")
            return

        # Authenticate via shared secret
        provided_secret = self.headers.get("X-Relay-Secret", "")
        if not RELAY_SECRET or not hmac.compare_digest(provided_secret, RELAY_SECRET):
            self.send_error(403, "Invalid relay secret")
            return

        # Extract headers
        recipient = self.headers.get("X-Recipient", "")
        sender = self.headers.get("X-Sender", "relay@zyntapos.com")
        if not recipient:
            self.send_error(400, "Missing X-Recipient header")
            return

        # Read raw email body
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length == 0:
            self.send_error(400, "Empty body")
            return
        raw_email = self.rfile.read(content_length)

        # Deliver via SMTP to Stalwart
        try:
            with smtplib.SMTP(STALWART_HOST, STALWART_PORT, timeout=30) as smtp:
                smtp.ehlo("email-relay.local")
                smtp.sendmail(sender, [recipient], raw_email)
            print(f"[relay] Delivered to {recipient} from {sender} ({len(raw_email)} bytes)")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"delivered"}')
        except smtplib.SMTPRecipientsRefused as e:
            print(f"[relay] Recipient refused: {recipient} - {e}")
            self.send_error(422, f"Recipient refused: {e}")
        except Exception as e:
            print(f"[relay] SMTP error: {e}")
            self.send_error(502, f"SMTP delivery failed: {e}")

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"ok")
            return
        self.send_error(404)

    def log_message(self, format, *args):
        # Suppress default access logging for health checks
        if "/health" not in (args[0] if args else ""):
            sys.stderr.write(f"[relay] {self.address_string()} - {format % args}\n")


if __name__ == "__main__":
    if not RELAY_SECRET:
        print("[relay] WARNING: RELAY_SECRET not set — authentication disabled!")
    print(f"[relay] Starting on port {LISTEN_PORT}, forwarding to {STALWART_HOST}:{STALWART_PORT}")
    server = HTTPServer(("0.0.0.0", LISTEN_PORT), RelayHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    server.server_close()
