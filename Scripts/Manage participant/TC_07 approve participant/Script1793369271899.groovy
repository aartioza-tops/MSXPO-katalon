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
import com.kms.katalon.core.testng.keyword.TestNGBuiltinKeywords as TestNGKW
import com.kms.katalon.core.testobject.TestObject as TestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords as Windows
import internal.GlobalVariable as GlobalVariable
import org.openqa.selenium.Keys as Keys

// ---------------------------------------------------------------------------
// TC_07 approve/reject participant (app.msxpo.sa)
// Driven by Data Files/Manage participant/TC_07 approve_reject participant:
//   email_address, password      -> admin login
//   participant_name             -> WHICH participant row to act on
//   approve_reject               -> 'approve' or 'reject'
//
// The Approve/Reject button is located INSIDE the target participant's row
// (row-scoped xpath), so it can only ever be clicked for the given participant.
// A confirmation dialog opens and is verified to mention the participant's
// name before confirming. Success = dialog closes and the row no longer shows
// Approve/Reject (participant left Pending).
//
// Selector notes (verified live on app.msxpo.sa):
//  - the participant name is a BUTTON inside the first cell (title='View
//    Details'), so the row predicate must go through that button; a td-level
//    normalize-space() would read name+event together and never match.
//  - the action buttons carry aria-label='Approve' / 'Reject'.
//  - the confirm dialog is div[role=dialog] with buttons named Approve/Reject.
// ---------------------------------------------------------------------------

String participant = participant_name.toString().trim()
String decision = approve_reject.toString().trim().toLowerCase()

assert participant : 'participant_name is empty - bind the test case to Data Files/Manage participant/TC_07 approve_reject participant'
assert (decision == 'approve' || decision == 'reject') : "approve_reject must be 'approve' or 'reject' but was '${approve_reject}'"
String action = decision.capitalize()

WebUI.comment("Participant '${participant}' -> ${action}")

// --- login ---
WebUI.openBrowser(null)
try {
	WebUI.maximizeWindow()
} catch (Exception e) {
	WebUI.comment('maximizeWindow skipped: ' + e.message)
}

// navigate with retry: the first navigation after Chrome starts can time out
String loginUrl = 'https://app.msxpo.sa/login'
boolean loaded = false
for (int navAttempt = 1; (navAttempt <= 3) && !loaded; navAttempt++) {
	try {
		WebUI.navigateToUrl(loginUrl)
		loaded = true
	} catch (Exception e) {
		WebUI.comment("navigateToUrl(${loginUrl}) attempt ${navAttempt} failed: ${e.message}")
		if (navAttempt < 3) {
			WebUI.delay(5)
		}
	}
}
assert loaded : "Could not open ${loginUrl} after 3 attempts"

WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your email'), email_address)
WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your password'), password)
WebUI.click(findTestObject('Page_MSxpo - Admin Panel/button_Sign In'))

// --- Participants list ---
TestObject participantsLink = findTestObject('Page_MSxpo - Admin Panel/span_Participants')
WebUI.waitForElementVisible(participantsLink, 30)
WebUI.click(participantsLink)

// --- row-scoped objects: the button can only be found in THIS participant's row ---
TestObject participantRow = findTestObject('Object Repository/Page_MSxpo - Admin Panel/participant_row', [('participant_name') : participant])
TestObject actionBtn = findTestObject('Object Repository/Page_MSxpo - Admin Panel/participant_action_button', [('participant_name') : participant, ('action') : action])
TestObject confirmDialog = findTestObject('Object Repository/Page_MSxpo - Admin Panel/confirm_dialog', [('participant_name') : participant])
TestObject confirmBtn = findTestObject('Object Repository/Page_MSxpo - Admin Panel/dialog_confirm_button', [('action') : action])

assert WebUI.waitForElementVisible(participantRow, 30) : "Participant '${participant}' not found in the Participants table"
assert WebUI.waitForElementVisible(actionBtn, 15) : "'${participant}' has no ${action} button - the participant is not Pending (already approved/rejected?)"

// --- click Approve/Reject for THIS row only ---
WebUI.click(actionBtn)

// --- confirm dialog must appear and must name the participant ---
assert WebUI.waitForElementVisible(confirmDialog, 10) : "Confirmation dialog for '${participant}' did not appear"
WebUI.comment('Dialog confirmed for: ' + participant)

WebUI.click(confirmBtn)

// --- success: dialog closed and the row left Pending (buttons gone) ---
assert WebUI.waitForElementNotPresent(confirmDialog, 15) : 'Confirmation dialog is still open after confirming'
assert WebUI.waitForElementNotPresent(actionBtn, 15) : "'${participant}' still shows ${action}/Reject buttons - ${action} did not take effect"
WebUI.comment("Verified: ${action} applied to '${participant}' (participant is no longer Pending)")

WebUI.closeBrowser()