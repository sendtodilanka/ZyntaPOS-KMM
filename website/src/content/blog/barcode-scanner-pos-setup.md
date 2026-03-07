---
title: "How to Set Up a Barcode Scanner with Your POS System"
description: "Connect a USB, Bluetooth, or camera barcode scanner to your POS system in minutes. This guide covers every scanner type, setup steps, and troubleshooting for ZyntaPOS and other platforms."
publishDate: 2025-12-16
author: "Zynta Team"
tags: ["Barcode Scanner", "POS Setup", "Retail Hardware", "Point of Sale"]
draft: false
---

Barcode scanning transforms your checkout speed from manually typing product names to a sub-second scan-and-add flow. A $40 USB barcode scanner can process a barcode in under 100ms — faster than any staff member can type, and error-free.

This guide covers the three types of barcode scanners used with POS systems, how to connect each type, and how to configure ZyntaPOS to work with them.

---

## Types of Barcode Scanners for POS

### 1. USB HID Barcode Scanners (Recommended)

USB HID (Human Interface Device) scanners are the workhorse of retail POS. They connect via USB cable and behave exactly like a keyboard — the scanned barcode is "typed" into whatever field has focus on screen.

**Why USB HID is the best choice for POS:**
- **Zero configuration required** — plug in the USB cable and it works immediately
- **No pairing, no Bluetooth dropouts, no battery charging**
- **Works with any POS software** — because it emulates a keyboard
- **Sub-100ms scan speed** — faster than Bluetooth
- **Reliable and durable** — no wireless interference issues
- **Cost:** $30–$150 for a quality unit

**Recommended models:**
- Zebra DS2208 ($60–$100) — excellent for retail, reads damaged barcodes
- Honeywell Voyager 1200G ($40–$80) — reliable, compact
- Datalogic QuickScan QD2500 ($50–$90) — fast, durable
- Generic HID scanner from Amazon ($25–$40) — adequate for low-volume use

### 2. Bluetooth Wireless Scanners

Bluetooth scanners offer freedom of movement — useful in warehouse receiving, stockroom management, or tableside ordering in restaurants. For a fixed checkout counter, USB is usually better.

**Considerations:**
- Requires pairing process (one-time, per device)
- Battery must be charged
- Occasional Bluetooth dropout (especially in crowded 2.4GHz environments)
- 2–3× higher price than USB equivalents
- Works with Android, Windows, macOS

**When to choose Bluetooth:**
- Stockroom receiving where you're moving around
- Tableside ordering in a restaurant
- Mobile / pop-up retail

### 3. Camera Barcode Scanning (Built into ZyntaPOS)

ZyntaPOS includes built-in camera barcode scanning powered by Google ML Kit. No external scanner hardware required — point the tablet camera at a barcode and it detects it automatically.

**Advantages:**
- Zero additional cost
- Works on any tablet with a rear camera
- Can scan barcodes at any angle
- Handles QR codes, DataMatrix, and all standard 1D barcodes

**Limitations:**
- Slower than USB (typically 0.5–2 seconds vs. <0.1 seconds for USB)
- Requires pointing camera at the barcode
- Not suitable for high-volume checkout queues

Camera scanning is ideal for:
- Product lookup (search by scanning)
- Receiving stock (scanning items as they arrive)
- Ad-hoc scanning when you've forgotten your USB scanner
- Mobile/pop-up use

---

## Connecting a USB Barcode Scanner to an Android Tablet

Android tablets have a USB port (usually USB-C). Most modern Android tablets support USB Host mode, which allows USB devices to connect.

### What you need:
- USB barcode scanner with USB-A connector
- USB-C to USB-A adapter (or USB hub) — most tablets don't include this
- Android tablet running Android 7.0+

### Setup steps:

1. **Check your tablet supports USB Host mode.** Most Android tablets do — if your tablet has a file manager that shows USB drives, USB Host is enabled.

2. **Connect the adapter** — plug your USB-C to USB-A adapter into the tablet port.

3. **Plug in the scanner** — connect the scanner's USB-A cable to the adapter. The tablet should show a notification "USB device connected."

4. **Open ZyntaPOS** and navigate to the POS checkout screen.

5. **Test the scanner** — scan any barcode. If the product exists in your catalogue, it should be added to the cart immediately.

6. **No drivers, no configuration needed** — USB HID works natively on Android.

### Troubleshooting USB scanner on Android:

**Problem:** Scanner blinks but nothing happens in the POS
**Solution:** The POS input field must have focus. Tap the product search bar first, then scan.

**Problem:** Scanner types characters but adds wrong product
**Solution:** Your barcode format may use Code128 or other non-EAN formats. Verify the barcode format matches what's stored in your product catalogue.

**Problem:** Scanner not recognised by tablet
**Solution:** Try a different USB hub or adapter. Some cheap adapters don't support USB Host properly.

---

## Connecting a USB Scanner to a Windows/Mac/Linux Desktop

Even simpler than Android:

1. Plug the USB cable into any available USB port
2. The scanner installs automatically as a HID keyboard device
3. Open ZyntaPOS desktop app
4. Navigate to the POS screen
5. Click the search/product field to give it keyboard focus
6. Scan a barcode — it types the barcode number and typically sends Enter

No additional setup required.

---

## Connecting a Bluetooth Scanner

### On Android:

1. Put the scanner in pairing mode (hold pairing button — refer to scanner manual)
2. On Android: **Settings → Bluetooth → Scan for devices**
3. Select your scanner from the device list
4. Accept pairing request on both devices
5. The scanner now behaves like a Bluetooth keyboard

ZyntaPOS will receive scan data automatically when the scanner is paired and active.

### On Windows:

1. Put scanner in pairing mode
2. **Settings → Devices → Add Bluetooth or other device → Bluetooth**
3. Select your scanner
4. Pair
5. Scanner appears as a Bluetooth HID keyboard device

---

## Setting Up Barcodes in ZyntaPOS

### Adding a barcode to an existing product

1. Go to **Inventory → Products**
2. Tap or click the product to edit
3. Find the **Barcode** field
4. Either type the barcode number manually, or tap the camera icon to scan the barcode on the physical product
5. Save the product

Now when you scan that barcode at checkout, the product is added to the cart instantly.

### Adding barcodes during product creation

When creating a new product:
1. Fill in name, price, category
2. In the **Barcode** field — scan the product's physical barcode with the tablet camera or USB scanner
3. The barcode is captured and saved with the product

### Importing products with barcodes via CSV

For bulk import, include a `barcode` column in your CSV:

```
name,price,category,barcode,stock_quantity
"Coca-Cola 330ml",1.50,Beverages,5449000000996,100
"Lays Original 40g",1.20,Snacks,4902420715001,80
```

Import the CSV in **Inventory → Import Products**. All barcodes are mapped automatically.

### Products with multiple barcodes

Some products have different barcodes on different packaging sizes (e.g., a 6-pack and individual can both scan to the same product). ZyntaPOS allows multiple barcodes per product — add each barcode in the product edit screen.

---

## Barcode Standards: What You Need to Know

Most retail products use one of these barcode standards:

| Standard | Format | Common Use |
|----------|--------|-----------|
| EAN-13 | 13 digits | European/international retail products |
| UPC-A | 12 digits | North American retail products |
| EAN-8 | 8 digits | Small packages |
| Code 128 | Variable length | Internal, shipping, warehouse |
| QR Code | 2D matrix | Digital products, URLs, internal use |
| DataMatrix | 2D matrix | Small components, medical devices |

ZyntaPOS and all modern USB HID scanners support all of the above formats. No configuration needed.

---

## Barcode Labels: Printing Your Own

If your products don't have barcodes (handmade goods, fresh produce, bulk items), you can print your own:

1. **Create a product** in ZyntaPOS and let the system generate an internal barcode (or enter your own number)
2. **Export the barcode data** — product name, price, barcode number
3. **Print labels** using a thermal label printer (Zebra ZD420, DYMO LabelWriter) or standard label sheets on a regular printer

Label printing software options:
- **ZebraDesigner** (free, for Zebra printers)
- **BarTender** (paid, professional label management)
- **Avery Design & Print** (free, for Avery label sheets)

---

## Checkout Speed with Barcode Scanning

To illustrate the impact of barcode scanning on checkout speed:

**Manual checkout (typing product names):**
- Average time per item: 8–12 seconds
- 10-item basket: 80–120 seconds
- Queue of 5 customers: 7–10 minutes

**Barcode scan checkout (USB HID scanner):**
- Average time per item: 0.5–1 second (scan + enter)
- 10-item basket: 5–10 seconds
- Queue of 5 customers: under 1 minute

Barcode scanning makes a 10–20× difference in checkout throughput. For any business with more than 20 products or more than 50 daily transactions, it's essential.

---

**[Download ZyntaPOS — USB barcode scanning works out of the box →](/download)**
