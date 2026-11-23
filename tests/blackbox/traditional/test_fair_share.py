
import json
import os
import uuid

import pytest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


URL = os.getenv("FAIR_SHARE_URL", "http://127.0.0.1:5173").rstrip("/")
UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
LOWER = "abcdefghijklmnopqrstuvwxyz"


@pytest.fixture
def driver():
    options = webdriver.ChromeOptions()
    options.add_argument("--incognito")
    options.add_argument("--window-size=1400,900")
    if os.getenv("HEADLESS") == "1":
        options.add_argument("--headless=new")
    browser = webdriver.Chrome(options=options)
    yield browser
    browser.quit()


def wait(driver):
    return WebDriverWait(driver, 15)


def text(driver):
    return driver.find_element(By.TAG_NAME, "body").text.lower()


def open_app(driver):
    driver.get(URL)
    wait(driver).until(EC.presence_of_element_located((By.TAG_NAME, "body")))


def field(driver, name):
    name = name.lower()

    def find(d):
        labels = d.find_elements(
            By.XPATH,
            f"//label[contains(translate(normalize-space(.),'{UPPER}','{LOWER}'),'{name}')]",
        )
        for label in labels:
            if not label.is_displayed():
                continue
            field_id = label.get_attribute("for")
            if field_id:
                matches = d.find_elements(By.ID, field_id)
                if matches and matches[0].is_displayed():
                    return matches[0]
            matches = label.find_elements(By.CSS_SELECTOR, "input, textarea")
            if matches:
                return matches[0]
        for attr in ("aria-label", "placeholder", "name", "id"):
            matches = d.find_elements(
                By.CSS_SELECTOR,
                f"input[{attr}*='{name}' i], textarea[{attr}*='{name}' i]",
            )
            for match in matches:
                if match.is_displayed():
                    return match
        return False

    return wait(driver).until(find)


def button(driver, name):
    name = name.lower()
    path = (
        f"//button[contains(translate(normalize-space(.),'{UPPER}','{LOWER}'),'{name}')]"
    )
    return wait(driver).until(EC.visibility_of_element_located((By.XPATH, path)))


def fill(element, value):
    element.clear()
    element.send_keys(value)


def sign_in(driver):
    email = os.getenv("FAIR_SHARE_TEST_EMAIL")
    password = os.getenv("FAIR_SHARE_TEST_PASSWORD")
    if not email or not password:
        pytest.skip("Development test account not configured")

    open_app(driver)
    fill(field(driver, "email"), email)
    fill(field(driver, "password"), password)
    login = button(driver, "sign in")
    if not login.is_enabled():
        pytest.skip("Firebase sign-in is not configured")
    login.click()
    wait(driver).until(lambda d: "create household" in text(d))


def create_household(driver, name=None):
    if name is None:
        name = "Selenium " + uuid.uuid4().hex[:10]
    fill(field(driver, "household name"), name)
    button(driver, "create household").click()
    wait(driver).until(lambda d: name.lower() in text(d))
    return name


def rejected(driver, submit, input_element=None):
    before = text(driver)
    if not submit.is_enabled():
        return
    submit.click()
    if input_element is not None:
        valid = driver.execute_script("return arguments[0].checkValidity()", input_element)
        if not valid:
            return
    errors = ("required", "invalid", "error", "must", "failed", "cannot", "too long")
    wait(driver).until(
        lambda d: text(d) != before and any(word in text(d) for word in errors)
    )


def test_b01_homepage_loads(driver):
    open_app(driver)
    assert "fair share" in text(driver)


def test_b02_backend_health(driver):
    driver.get(URL + "/api/health")
    result = json.loads(driver.find_element(By.TAG_NAME, "body").text)
    assert result["status"] == "UP"


def test_b03_wrong_password(driver):
    open_app(driver)
    fill(field(driver, "email"), "unknown-" + uuid.uuid4().hex[:8] + "@example.com")
    fill(field(driver, "password"), "incorrect-password-123")
    login = button(driver, "sign in")
    if not login.is_enabled():
        pytest.skip("Firebase sign-in is not configured")
    login.click()
    wait(driver).until(lambda d: any(word in text(d) for word in (
        "invalid", "incorrect", "wrong", "failed", "error"
    )))
    assert "create household" not in text(driver)


def test_b04_correct_login(driver):
    sign_in(driver)
    assert button(driver, "create household").is_displayed()


def test_b05_sign_out(driver):
    sign_in(driver)
    button(driver, "sign out").click()
    wait(driver).until(lambda d: any(e.is_displayed() for e in d.find_elements(
        By.CSS_SELECTOR, "input[type='password']"
    )))
    assert "create household" not in text(driver)


def test_b06_blank_household_name(driver):
    sign_in(driver)
    name = field(driver, "household name")
    fill(name, "   ")
    rejected(driver, button(driver, "create household"), name)


def test_b07_household_name_at_limit(driver):
    sign_in(driver)
    name = "H" * 80
    create_household(driver, name)
    assert name.lower() in text(driver)


def test_b08_household_name_over_limit(driver):
    sign_in(driver)
    name = "H" * 81
    name_input = field(driver, "household name")
    fill(name_input, name)
    if len(name_input.get_attribute("value")) <= 80:
        assert len(name_input.get_attribute("value")) == 80
    else:
        rejected(driver, button(driver, "create household"), name_input)
        assert name.lower() not in text(driver)


def test_b09_create_household(driver):
    sign_in(driver)
    name = create_household(driver)
    assert name.lower() in text(driver)


def test_b10_negative_expense(driver):
    sign_in(driver)
    create_household(driver)
    fill(field(driver, "description"), "Invalid amount")
    amount = field(driver, "amount")
    fill(amount, "-1")
    rejected(driver, button(driver, "save expense"), amount)


def test_b11_expense_without_description(driver):
    sign_in(driver)
    create_household(driver)
    description = field(driver, "description")
    fill(description, "")
    fill(field(driver, "amount"), "10.00")
    rejected(driver, button(driver, "save expense"), description)


def test_b12_save_and_view_expense(driver):
    sign_in(driver)
    create_household(driver)
    name = "Groceries " + uuid.uuid4().hex[:6]
    fill(field(driver, "description"), name)
    fill(field(driver, "amount"), "12.00")
    button(driver, "save expense").click()
    wait(driver).until(lambda d: "saved" in text(d))
    button(driver, "view " + name).click()
    wait(driver).until(lambda d: "participant shares" in text(d))
    assert name.lower() in text(driver)
