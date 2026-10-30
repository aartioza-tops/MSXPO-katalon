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
import org.openqa.selenium.WebElement as WebElement

// ---------------------------------------------------------------------------
// TC_04 Assign booth (msx-dev.msxpo.sa)
// Assigns booth 'booth_number' (data file column) to participant 'ParticipantName':
//   login -> Participants -> participant row -> Booth Requests tab -> Assign Booth
//   -> release the booth first if this participant already holds it (keeps the run
//      deterministic and always exercises a real assignment)
//   -> tick the booth checkbox -> Update -> reopen Assign Booth -> verify it saved.
//
// App rules verified live on msx-dev:
//  - The Assign Booth action opens the exhibitor EDIT form
//    (/exhibitors/{id}/edit) whose booth checkboxes are DISABLED when the booth
//    belongs to another participant - only a free booth (or one this participant
//    already holds) can be ticked.
//  - Update saves via PUT /api/v1/exhibitors/{id} and redirects back to the
//    participants list (/exhibitors); the row's View Details button reappearing
//    is the success signal.
//  - Persistence is confirmed by reopening Assign Booth and re-reading the
//    checkbox state (not just trusting the redirect).
//
// Timing notes (from run 20261030_154423):
//  - the first navigation after Chrome starts sometimes hits a Selenium
//    page-load TimeoutException on this environment -> navigate with retries.
//  - the booth list on the edit form renders SLOWLY (booth-by-hall data is a
//    separate fetch; >25s observed on a cold dev API) -> wait up to 60s.
// ---------------------------------------------------------------------------

String participantName = ParticipantName.toString().trim()
String boothNo = booth_number.toString().trim()

assert participantName : 'ParticipantName is empty - bind the test case to Data Files/Manage participant/TC_04 Assign booth'
assert boothNo : 'booth_number is empty - bind the test case to Data Files/Manage participant/TC_04 Assign booth'
WebUI.comment("Assigning booth ${boothNo} to participant ${participantName}")

// --- login ---
WebUI.openBrowser(null)
try {
	WebUI.maximizeWindow()
} catch (Exception e) {
	WebUI.comment('maximizeWindow skipped: ' + e.message)
}

// navigate with retry: the first navigation after Chrome starts can time out
String loginUrl = 'https://msx-dev.msxpo.sa/login'
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

// --- reusable objects (parameterized) ---
TestObject participantRow = findTestObject('Object Repository/Page_MSxpo - Admin Panel/participant_view_details', [('ParticipantName') : participantName])
TestObject boothRequestsTab = findTestObject('Object Repository/Page_MSxpo - Admin Panel/tab_Booth Requests')
TestObject assignBoothBtn = findTestObject('Object Repository/Page_MSxpo - Admin Panel/button_Assign Booth')
TestObject boothCheckbox = findTestObject('Object Repository/Page_MSxpo - Admin Panel/checkbox_Booth', [('booth_number') : boothNo])
TestObject updateBtn = findTestObject('Page_MSxpo - Admin Panel/button_Update')

// closure: from the participants list, open the Assign Booth form for the participant.
// The booth list renders slowly (separate booths-by-hall fetch on a cold API),
// so allow up to 60s for the checkbox to appear.
def openAssignBooth = {
	assert WebUI.waitForElementVisible(participantRow, 30) : "Participant row '${participantName}' not found in the Participants table"
	WebUI.click(participantRow)
	assert WebUI.waitForElementVisible(boothRequestsTab, 20) : "Booth Requests tab not found on the participant detail page"
	WebUI.click(boothRequestsTab)
	assert WebUI.waitForElementVisible(assignBoothBtn, 20) : "Assign Booth button not found on the Booth Requests tab"
	WebUI.click(assignBoothBtn)
	assert WebUI.waitForElementPresent(boothCheckbox, 60) : "Booth ${boothNo} checkbox did not render on the Assign Booth form within 60s"
}

openAssignBooth()

// --- the booth must be assignable (free, or already this participant's own) ---
WebElement boothInput = WebUI.findWebElement(boothCheckbox, 10)
assert boothInput.isEnabled() : "Booth ${boothNo} belongs to another participant - pick a booth that is free for '${participantName}'"

// --- release first when this participant already holds the booth ---
if (boothInput.isSelected()) {
	WebUI.comment("Booth ${boothNo} is already assigned to '${participantName}' - releasing it first so this run performs a real assignment")
	WebUI.click(boothCheckbox)
	assert !WebUI.findWebElement(boothCheckbox, 10).isSelected() : "Booth ${boothNo} could not be released"
	WebUI.click(updateBtn)
	// Update redirects back to the participants list
	WebUI.waitForElementVisible(participantRow, 30)
	openAssignBooth()
	WebUI.comment("Booth ${boothNo} released - reassigning")
}

// --- assign the booth ---
WebUI.click(boothCheckbox)
assert WebUI.findWebElement(boothCheckbox, 10).isSelected() : "Booth ${boothNo} could not be selected for '${participantName}'"
WebUI.comment("Booth ${boothNo} selected - clicking Update")

WebUI.click(updateBtn)
// success signal: Update redirected back to the participants list
WebUI.waitForElementVisible(participantRow, 30)

// --- verify persistence: reopen Assign Booth and re-read the checkbox ---
openAssignBooth()
assert WebUI.findWebElement(boothCheckbox, 10).isSelected() : "Booth ${boothNo} was NOT saved as assigned to '${participantName}'"
WebUI.comment("Verified: booth ${boothNo} is assigned to '${participantName}'")

WebUI.closeBrowser()