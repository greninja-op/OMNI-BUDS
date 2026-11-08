import os
from playwright.sync_api import sync_playwright

def capture_sections():
    brain_dir = r'C:\Users\Athira Aswin\.gemini\antigravity\brain\22671a70-68a0-4ae2-9f7b-f5dd436af6de'
    
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=r'C:\Program Files\Google\Chrome\Application\chrome.exe')
        page = browser.new_page(viewport={'width': 1440, 'height': 900})
        page.goto('http://localhost:3000/index.html')
        page.wait_for_timeout(1000)

        # 1. Hero
        hero = page.locator('#hero')
        hero.screenshot(path=os.path.join(brain_dir, 'pw_hero.png'))
        print('Captured pw_hero.png')

        # 2. Auto-connect
        auto = page.locator('#auto-connect')
        auto.scroll_into_view_if_needed()
        page.wait_for_timeout(400)
        auto.screenshot(path=os.path.join(brain_dir, 'pw_auto_connect.png'))
        print('Captured pw_auto_connect.png')

        # 3. Notification Controls
        noti = page.locator('#notification-controls')
        noti.scroll_into_view_if_needed()
        page.wait_for_timeout(400)
        noti.screenshot(path=os.path.join(brain_dir, 'pw_notification_controls.png'))
        print('Captured pw_notification_controls.png')

        # 4. Features
        feats = page.locator('#features')
        feats.scroll_into_view_if_needed()
        page.wait_for_timeout(400)
        feats.screenshot(path=os.path.join(brain_dir, 'pw_features.png'))
        print('Captured pw_features.png')

        # 5. Comparison
        comp = page.locator('#comparison')
        comp.scroll_into_view_if_needed()
        page.wait_for_timeout(400)
        comp.screenshot(path=os.path.join(brain_dir, 'pw_comparison.png'))
        print('Captured pw_comparison.png')

        browser.close()

if __name__ == '__main__':
    capture_sections()
