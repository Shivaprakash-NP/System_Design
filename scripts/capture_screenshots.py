import time
import os
import requests
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:5173"
API_URL = "http://localhost:8080/api"
OUT_DIR = "/home/shiva/SDLAB/report/report-assets"
os.makedirs(OUT_DIR, exist_ok=True)

def capture():
    print("Starting comprehensive UI screenshot capture with Playwright Firefox...")
    with sync_playwright() as p:
        browser = p.firefox.launch(headless=True)
        context = browser.new_context(viewport={"width": 1280, "height": 820})
        page = context.new_page()

        # 1. Login Page
        print("Capturing 1. Login Page...")
        page.goto(f"{BASE_URL}/login")
        page.wait_for_selector("text=Sign In")
        time.sleep(1)
        page.screenshot(path=os.path.join(OUT_DIR, "auth_login.png"))

        # 2. Search Page & 3. Autocomplete
        print("Capturing 2. Hotel Search...")
        page.goto(f"{BASE_URL}/search")
        page.wait_for_selector("text=Featured Hotels")
        time.sleep(1.5)
        page.screenshot(path=os.path.join(OUT_DIR, "hotel_search.png"))

        # 3. Autocomplete Dropdown
        print("Capturing 3. Autocomplete Dropdown...")
        search_input = page.locator("input[placeholder*='Search hotel name']")
        search_input.click()
        search_input.type("Taj", delay=120)
        time.sleep(1.5)
        page.screenshot(path=os.path.join(OUT_DIR, "autocomplete_dropdown.png"))

        # 4. Booking Modal
        print("Capturing 4. Booking Modal...")
        search_input.fill("")
        page.keyboard.press("Escape")
        time.sleep(1)
        # Find room cards and click Reserve
        book_btns = page.locator("button:has-text('Reserve Room'), button:has-text('Book')")
        if book_btns.count() > 0:
            book_btns.first.click()
            time.sleep(1)
            page.screenshot(path=os.path.join(OUT_DIR, "booking_modal.png"))
            page.keyboard.press("Escape")
            time.sleep(0.5)

        # 5. Guest Login & Dashboard
        print("Capturing 5. Guest Dashboard...")
        page.goto(f"{BASE_URL}/login")
        page.wait_for_selector("text=Sign In")
        page.click("button:has-text('Guest Demo')")
        time.sleep(0.5)
        page.click("button[type='submit']")
        page.wait_for_url("**/search")
        time.sleep(1)

        page.goto(f"{BASE_URL}/my-bookings")
        time.sleep(2)
        page.screenshot(path=os.path.join(OUT_DIR, "guest_bookings.png"))

        # 6. Receptionist Login & Dashboard
        print("Capturing 6. Receptionist Dashboard...")
        page.goto(f"{BASE_URL}/login")
        page.wait_for_selector("text=Sign In")
        page.click("button:has-text('Receptionist')")
        time.sleep(0.5)
        page.click("button[type='submit']")
        page.wait_for_url("**/reception")
        time.sleep(2)
        page.screenshot(path=os.path.join(OUT_DIR, "reception_desk.png"))

        # Check if any receipt button exists to click, or click Checkout on a checked-in booking
        receipt_btns = page.locator("button:has-text('Receipt')")
        invoice_captured = False
        if receipt_btns.count() > 0:
            receipt_btns.first.click()
            time.sleep(2)
            page.screenshot(path=os.path.join(OUT_DIR, "invoice_billing.png"))
            invoice_captured = True
            print("Captured invoice via Reception Receipt button.")

        if not invoice_captured:
            # Let's see if check-out button exists
            checkout_btns = page.locator("button:has-text('Process Check-Out & Bill')")
            if checkout_btns.count() > 0:
                checkout_btns.first.click()
                time.sleep(2)
                # Now receipt button might be there
                receipt_btns = page.locator("button:has-text('Receipt')")
                if receipt_btns.count() > 0:
                    receipt_btns.first.click()
                    time.sleep(2)
                    page.screenshot(path=os.path.join(OUT_DIR, "invoice_billing.png"))
                    invoice_captured = True
                    print("Checked out booking and captured invoice!")

        # 7. Housekeeping Login & Dashboard
        print("Capturing 7. Housekeeping Dashboard...")
        page.goto(f"{BASE_URL}/login")
        page.wait_for_selector("text=Sign In")
        page.click("button:has-text('Housekeeping')")
        time.sleep(0.5)
        page.click("button[type='submit']")
        page.wait_for_url("**/housekeeping")
        time.sleep(2)
        page.screenshot(path=os.path.join(OUT_DIR, "housekeeping_dashboard.png"))

        # 8. System Design Dashboard
        print("Capturing 8. System Design Dashboard...")
        page.goto(f"{BASE_URL}/system-design")
        time.sleep(2.5)
        page.screenshot(path=os.path.join(OUT_DIR, "system_design_dashboard.png"))

        browser.close()
        print("Screenshot capture routine complete!")

if __name__ == "__main__":
    capture()
