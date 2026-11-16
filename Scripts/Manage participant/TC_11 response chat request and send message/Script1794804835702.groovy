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
// TC_11 response chat request and send message  (app.msxpo.sa /chat)
// Data columns bound at suite level:
//   email_address, password, visitor_name, event_name, hall_name,
//   Booth_name, Response (Accept|Reject), Message
//
// Repaired 2026-11-16:
//   - the request row is now matched on ALL FOUR data fields
//     (visitor + event + hall + booth) instead of the recorded
//     positional //li[1], so the script only ever reacts to the request
//     described by the test data and never touches another request
//   - Accept vs Reject is decided by the 'Response' column
//   - Message is typed/sent ONLY when Response = Accept (after a Reject
//     there is no conversation to type into)
//   - assertions added: login ok, matching row found, row gone after the
//     response, message visible after send
//
// Pending-request row DOM (verified live on app.msxpo.sa /chat):
//   <li class="m-2 rounded-lg ...">
//     <p class="... text-slate-900">Visitor</p>
//     <p class="... text-[11px] ...">Event</p>
//     <p class="... text-[11px] ...">Hall</p>
//     <p class="... text-amber-700">Booth</p>
//     <button ...>Accept</button>
//     <button aria-label="Reject">X</button>   <- icon only, no text
// ===========================================================================
WebUI.openBrowser(null)

WebUI.navigateToUrl('https://app.msxpo.sa/login')

WebUI.maximizeWindow()

WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your email'), email_address)

WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your password'), password)

WebUI.click(findTestObject('Page_MSxpo - Admin Panel/button_Sign In'))

// ---------------------------------------------------------------------------
// Login check: the admin sidebar (Messaging link) only exists after a
// successful sign-in, so a bad login fails here with a clear message
// instead of timing out later on the chat page.
// ---------------------------------------------------------------------------
TestObject messagingLink = findTestObject('Page_MSxpo - Admin Panel/span_Messaging')

assert WebUI.waitForElementPresent(messagingLink, 30) : 'Login failed - Messaging link not visible after Sign In'

WebUI.click(messagingLink)

// ---------------------------------------------------------------------------
// Locate THE pending request matching the test data - exact text match on
// all four fields inside a single row (rounded-lg = pending request item).
// Fails if no matching row exists; a non-matching request is never clicked.
// NOTE: data values must not contain an apostrophe (XPath string literal).
// ---------------------------------------------------------------------------
String rowXpath = "//li[contains(concat(' ', normalize-space(@class), ' '), ' rounded-lg ')]" +
	"[.//p[contains(concat(' ', normalize-space(@class), ' '), ' text-slate-900 ') and normalize-space(.) = '" + visitor_name + "']]" +
	"[.//p[contains(concat(' ', normalize-space(@class), ' '), ' text-[11px] ') and normalize-space(.) = '" + event_name + "']]" +
	"[.//p[contains(concat(' ', normalize-space(@class), ' '), ' text-[11px] ') and normalize-space(.) = '" + hall_name + "']]" +
	"[.//p[contains(concat(' ', normalize-space(@class), ' '), ' text-amber-700 ') and normalize-space(.) = '" + Booth_name + "']]"

TestObject matchedRow = new TestObject('matchedChatRequestRow')

matchedRow.addProperty('xpath', ConditionType.EQUALS, rowXpath)

assert WebUI.waitForElementPresent(matchedRow, 30) : ('No pending chat request matching the test data: visitor=' + visitor_name + ', event=' + event_name + ', hall=' + hall_name + ', booth=' + Booth_name)

WebUI.comment('Matched pending chat request: ' + visitor_name + ' / ' + event_name + ' / ' + hall_name + ' / ' + Booth_name)

// ---------------------------------------------------------------------------
// Response column decides Accept or Reject (case-insensitive)
// ---------------------------------------------------------------------------
String response = (Response ?: '').trim()

if (response.equalsIgnoreCase('Accept')) {
    // ---- ACCEPT: click Accept INSIDE the matched row --------------------
    TestObject acceptBtn = new TestObject('acceptBtnInMatchedRow')

    acceptBtn.addProperty('xpath', ConditionType.EQUALS, rowXpath + '//button[normalize-space(.) = \'Accept\']')

    assert WebUI.waitForElementPresent(acceptBtn, 10) : 'Accept button not found inside the matched request row'

    WebUI.click(acceptBtn)

    // the request must be consumed - the row disappears from Pending Requests
    assert WebUI.waitForElementNotPresent(matchedRow, 20) : 'Request row still listed after clicking Accept'

    // the conversation opens automatically; wait for the enabled composer
    TestObject msgInput = new TestObject('enabledMessageInput')

    msgInput.addProperty('xpath', ConditionType.EQUALS, '//input[contains(concat(\' \', normalize-space(@class), \' \'), \' file:text-sm \') and not(@disabled)]')

    assert WebUI.waitForElementPresent(msgInput, 30) : 'Message input did not appear (enabled) after Accept'

    // ---- send the message from the data column --------------------------
    assert Message != null && Message.toString().trim() : 'Response is Accept but the Message column is empty'

    WebUI.setText(msgInput, Message)

    TestObject sendBtn = new TestObject('enabledSendButton')

    sendBtn.addProperty('xpath', ConditionType.EQUALS, '//button[@aria-label = \'Send\' and not(@disabled)]')

    assert WebUI.waitForElementPresent(sendBtn, 10) : 'Send button not enabled after typing the message'

    WebUI.click(sendBtn)

    // ---- verify the message reached the conversation --------------------
    TestObject sentBubble = new TestObject('sentMessageBubble')

    sentBubble.addProperty('xpath', ConditionType.EQUALS, '//div[contains(concat(\' \', normalize-space(@class), \' \'), \' rounded-2xl \')][contains(., \'' + Message + '\')]')

    assert WebUI.waitForElementPresent(sentBubble, 20) : ('Message not visible in the conversation after Send: ' + Message)

    // the app appends the bubble optimistically and ROLLS IT BACK if the
    // send fails, so re-check after a delay to make sure it really stuck
    WebUI.delay(3)

    assert WebUI.waitForElementPresent(sentBubble, 5) : ('Message disappeared after Send (delivery failed): ' + Message)

    WebUI.comment('Chat request accepted and message sent: ' + Message)
} else if (response.equalsIgnoreCase('Reject')) {
    // ---- REJECT: click the X (aria-label=Reject) INSIDE the matched row --
    TestObject rejectBtn = new TestObject('rejectBtnInMatchedRow')

    rejectBtn.addProperty('xpath', ConditionType.EQUALS, rowXpath + '//button[@aria-label = \'Reject\']')

    assert WebUI.waitForElementPresent(rejectBtn, 10) : 'Reject button not found inside the matched request row'

    WebUI.click(rejectBtn)

    // the request must be consumed - the row disappears from Pending Requests
    assert WebUI.waitForElementNotPresent(matchedRow, 20) : 'Request row still listed after clicking Reject'

    WebUI.comment('Chat request rejected: ' + visitor_name + ' / ' + Booth_name)
} else {
    assert false : ('Response column must be Accept or Reject, got: \'' + Response + '\'')
}
