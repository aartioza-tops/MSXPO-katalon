import static com.kms.katalon.core.checkpoint.CheckpointFactory.findCheckpoint
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject
import static com.kms.katalon.core.testobject.ObjectRepository.findWindowsObject
import com.kms.katalon.core.checkpoint.Checkpoint as Checkpoint
import com.kms.katalon.core.cucumber.keyword.CucumberBuiltinKeywords as CucumberKW
import com.kms.katalon.core.llm.keyword.LlmKeywords as LLM
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testcase.TestCase as TestCase
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.testobject.ConditionType as ConditionType
import com.kms.katalon.core.testobject.TestObject as TestObject
import com.kms.katalon.core.testng.keyword.TestNGBuiltinKeywords as TestNGKW
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords as Windows
import internal.GlobalVariable as GlobalVariable
import org.openqa.selenium.Keys as Keys

// ===========================================================================
// TC_10 schedule meeting  (demo.msxpo.sa)
// Data columns bound at suite level:
//   email_address, password, event, booth, date (dd-MM-yyyy), time (9:30 am)
// Flow: login -> dashboard -> MSXPO logo -> Events -> event card (data event)
//       -> Participants -> Schedule a Meeting (booth) -> NEW tab on
//       scheduling.msxpo.sa -> day (data date) -> time (data time)
//       -> Confirm -> "Your Booth Meeting is Confirmed!"
// Repaired 2026-11-13:
//   - removed bogus setText(password_1, <encrypted string>) step that
//     corrupted the password (caused 401 / registration side-effects)
//   - login now uses span_Sign In (the real submit) exactly like TC_08,
//     then verifies the dashboard instead of clicking //button blindly
//     (first //button on the register page is the "Create Account" tab,
//      which sent earlier runs into the verification-code page)
// ===========================================================================
WebUI.openBrowser(null)

WebUI.navigateToUrl('https://demo.msxpo.sa/')

WebUI.maximizeWindow()

// ---------------------------------------------------------------------------
// Login (same proven sequence as TC_08)
// ---------------------------------------------------------------------------
WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/a_Sign In'))

WebUI.setText(findTestObject('Page_MSxpo  Virtual Expo Platform/input_Enter your email address'), email_address)

WebUI.setText(findTestObject('Page_MSxpo  Virtual Expo Platform/input_Enter your password'), password)

WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/span_Sign In'))

WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/div_Welcome backRedirecting to Dashboard in 58'))

WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/button_Go to Dashboard'))

// dashboard is the expected landing; tolerate an optional "Go to Dashboard"
// interstitial before it
TestObject expoHallBtn = findTestObject('Page_MSxpo  Virtual Expo Platform/button_Enter Expo Hall')

if (!(WebUI.waitForElementPresent(expoHallBtn, 20, FailureHandling.OPTIONAL))) {
    TestObject goDashboard = new TestObject('goDashboard')

    goDashboard.addProperty('xpath', ConditionType.EQUALS, '//button[normalize-space()=\'Go to Dashboard\']')

    assert WebUI.waitForElementPresent(goDashboard, 5)

    WebUI.click(goDashboard)

    assert WebUI.waitForElementPresent(expoHallBtn, 15) : 'dashboard not shown after clicking Go to Dashboard'
}

// ---------------------------------------------------------------------------
// Dashboard -> MSXPO logo -> homepage -> Events
// ---------------------------------------------------------------------------
WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/img_MSXPO'))

TestObject eventsLink = findTestObject('Page_MSxpo  Virtual Expo Platform/a_Events')

assert WebUI.waitForElementPresent(eventsLink, 15) : 'Events link not found on the homepage'

WebUI.click(eventsLink)

// ---------------------------------------------------------------------------
// Open the event card - data driven by column 'event'
// ---------------------------------------------------------------------------
TestObject eventCard = new TestObject('eventCard')

eventCard.addProperty('xpath', ConditionType.EQUALS, "//h3[normalize-space()='$event']")

assert WebUI.waitForElementPresent(eventCard, 15)

WebUI.click(eventCard)

// ---------------------------------------------------------------------------
// Participants tab
// ---------------------------------------------------------------------------
TestObject participantsTab = new TestObject('participantsTab')

participantsTab.addProperty('xpath', ConditionType.EQUALS, '//button[normalize-space()=\'Participants\']')

assert WebUI.waitForElementPresent(participantsTab, 15) : 'Participants tab not found on the event page'

WebUI.click(participantsTab)

// ---------------------------------------------------------------------------
// Schedule a Meeting - click ONLY the button belonging to the booth from
// column 'booth'. Its title must be exactly "Schedule a Meeting · <booth>"
// (· = U+00B7), so a partial booth value can never match another booth's
// button, and a booth that is not on the page fails instead of clicking
// anything else. The button opens a NEW tab on scheduling.msxpo.sa.
// ---------------------------------------------------------------------------
TestObject scheduleBtn = new TestObject('scheduleBtn')

scheduleBtn.addProperty('xpath', ConditionType.EQUALS, "//button[@title = concat('Schedule a Meeting · ', '$booth') and not(@disabled)]")

assert WebUI.waitForElementPresent(scheduleBtn, 20)

WebUI.click(scheduleBtn)

// ---------------------------------------------------------------------------
// Switch to the scheduling tab (scheduling.msxpo.sa / Booth Meeting)
// ---------------------------------------------------------------------------
boolean switched = false

for (int i = 0; (i < 15) && !(switched); i++) {
    try {
        WebUI.switchToWindowIndex(1)
    }
    catch (Throwable ignored) {
    } 
    // the new tab is not open yet - retry after a short delay
    
    switched = WebUI.getUrl().contains('scheduling.msxpo.sa')

    if (!(switched)) {
        WebUI.delay(1)
    }
}

assert switched : 'scheduling (Booth Meeting) tab did not open'

// ---------------------------------------------------------------------------
// Pick the day - data driven by column 'date' (dd-MM-yyyy)
// ---------------------------------------------------------------------------
String dayNumber = (date.split('-')[0]).trim().replaceFirst('^0', '')

TestObject dayCell = new TestObject('dayCell')

dayCell.addProperty('xpath', ConditionType.EQUALS, "//button[normalize-space()='$dayNumber']")

assert WebUI.waitForElementPresent(dayCell, 20)

WebUI.click(dayCell)

// ---------------------------------------------------------------------------
// Pick the time slot - data driven by column 'time' ('9:30 am' -> '9:30am')
// ---------------------------------------------------------------------------
String timeSlot = time.replaceAll('\\s+', '')

TestObject timeCell = new TestObject('timeCell')

timeCell.addProperty('xpath', ConditionType.EQUALS, "//button[@data-testid='time' and normalize-space()='$timeSlot']")

assert WebUI.waitForElementPresent(timeCell, 20)

String slotState = WebUI.getAttribute(timeCell, 'data-disabled')

assert slotState == 'false'

WebUI.click(timeCell)

// ---------------------------------------------------------------------------
// Booking form (name and email are prefilled from the URL) -> Confirm
// ---------------------------------------------------------------------------
TestObject confirmBtn = new TestObject('confirmBtn')

confirmBtn.addProperty('xpath', ConditionType.EQUALS, '//button[normalize-space()=\'Confirm\']')

assert WebUI.waitForElementPresent(confirmBtn, 20) : 'booking form (Confirm button) did not appear after choosing a time'

WebUI.click(confirmBtn)

// ---------------------------------------------------------------------------
// Success page
// ---------------------------------------------------------------------------
TestObject successMsg = new TestObject('successMsg')

successMsg.addProperty('xpath', ConditionType.EQUALS, '//*[contains(., \'Your Booth Meeting is Confirmed!\')]')

assert WebUI.waitForElementPresent(successMsg, 20) : 'booking success page did not appear after Confirm'

assert WebUI.verifyTextPresent('Your Booth Meeting is Confirmed!', false) : 'success message text not found on the booking page'

