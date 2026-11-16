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
// TC_12 upload media  (app.msxpo.sa)
// Data columns bound at suite level:
//   email_address, password, event_name, participant_name,
//   booth_name, path (unused - fixed path below), purpose
//
// Repaired 2026-11-16 (recorded script was broken):
//   - event is now selected from the test data via the real combobox
//     (role=combobox / role=option) instead of the recorded //*[@dir='auto']
//     which matched the first dir=auto element on the page
//   - participant is clicked by exact name inside the list row
//     (recorded span_Test 11_1 was positional)
//   - booth is located by exact booth_name inside its table row, then the
//     Media button of THAT row is clicked (dialog title = "<booth> — Media")
//   - file upload: recorded script did setText(fileInput, '"C:\\..."') with
//     literal quotes around the path (invalid path) and clicked the drop zone
//     first (risks a native file chooser under WebDriver). Now uses
//     WebUI.uploadFile() with the unquoted fixed path (uploadFile is the
//     keyword available in this Katalon build - uploadFilePath does not exist)
//   - option matching uses an exact-text DESCENDANT check: option textContent
//     concatenates label+subtitle with NO whitespace ("Test 11Franchise
//     gateway"), so starts-with with a trailing space never matches
//   - queue check uses the real DOM text "Ready to upload" (the uppercase
//     "READY TO UPLOAD" is CSS text-transform only and never matches XPath)
//   - purpose is selected from the test data ('Screen 1') instead of the
//     hardcoded stale span_Screen 2 object (radix id changes every render)
//   - assertions added: login, event option, participant row, booths tab,
//     booth row, dialog, file staged, purpose option, upload completed
//
// NOTE: data values must not contain an apostrophe (XPath string literal).
// ===========================================================================
WebUI.openBrowser(null)

WebUI.navigateToUrl('https://app.msxpo.sa/login')

WebUI.maximizeWindow()

WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your email'), email_address)

WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your password'), password)

WebUI.click(findTestObject('Page_MSxpo - Admin Panel/button_Sign In'))

// ---------------------------------------------------------------------------
// Login check: the admin sidebar (Messaging link) only exists after a
// successful sign-in, so a bad login fails here with a clear message.
// ---------------------------------------------------------------------------
TestObject messagingLink = findTestObject('Page_MSxpo - Admin Panel/span_Messaging')

assert WebUI.waitForElementPresent(messagingLink, 30) : 'Login failed - Messaging link not visible after Sign In'

// ---------------------------------------------------------------------------
// Go to Participants list
// ---------------------------------------------------------------------------
TestObject participantsLink = findTestObject('Page_MSxpo - Admin Panel/span_Participants')

assert WebUI.waitForElementPresent(participantsLink, 30) : 'Participants link not visible in the sidebar'

WebUI.click(participantsLink)

// ---------------------------------------------------------------------------
// 1) Select EVENT from test data.
//    The event combobox is the only role=combobox whose label is not the
//    rows-per-page selector ("10"). Works for both states: already selected
//    (aria-label "Selected: <event>") and placeholder.
// ---------------------------------------------------------------------------
TestObject eventCombo = new TestObject('eventCombo')

eventCombo.addProperty('xpath', ConditionType.EQUALS, '//button[@role = \'combobox\'][not(normalize-space(.) = \'10\')]')

assert WebUI.waitForElementClickable(eventCombo, 30) : 'Event dropdown not found on the Participants page'

WebUI.click(eventCombo)

TestObject eventOption = new TestObject('eventOption')

eventOption.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'listbox\']//*[@role = \'option\'][normalize-space(.) = \'' + event_name + '\' or .//*[normalize-space(.) = \'' + event_name + '\']]')

assert WebUI.waitForElementClickable(eventOption, 15) : ('Event \'' + event_name + '\' not found in the event dropdown')

WebUI.click(eventOption)

// ---------------------------------------------------------------------------
// 2) Select PARTICIPANT from test data - exact text match on the row's
//    name button (title="View Details"); clicking it opens the detail page.
// ---------------------------------------------------------------------------
TestObject participantBtn = new TestObject('participantRowButton')

participantBtn.addProperty('xpath', ConditionType.EQUALS, '//tbody//button[normalize-space(.) = \'' + participant_name + '\']')

assert WebUI.waitForElementClickable(participantBtn, 30) : ('Participant \'' + participant_name + '\' not listed for event \'' + event_name + '\'')

WebUI.click(participantBtn)

// ---------------------------------------------------------------------------
// 3) Open the Booth Requests tab on the participant detail page
// ---------------------------------------------------------------------------
TestObject boothsTab = new TestObject('boothsTab')

boothsTab.addProperty('xpath', ConditionType.EQUALS, '//button[normalize-space(.) = \'Booth Requests\']')

assert WebUI.waitForElementClickable(boothsTab, 30) : 'Booth Requests tab not found on the participant detail page'

WebUI.click(boothsTab)

// ---------------------------------------------------------------------------
// 4) Locate THE booth row by exact booth_name, then click the Media button
//    ("Add Media" / later "Manage" etc.) inside THAT row only.
//    Fails if the booth does not exist - another booth is never touched.
// ---------------------------------------------------------------------------
TestObject boothMediaBtn = new TestObject('boothRowMediaButton')

boothMediaBtn.addProperty('xpath', ConditionType.EQUALS, '//tr[.//*[normalize-space(.) = \'' + booth_name + '\']]//button[contains(normalize-space(.), \'Media\')]')

assert WebUI.waitForElementClickable(boothMediaBtn, 30) : ('Booth \'' + booth_name + '\' not found (or has no Media action) for participant \'' + participant_name + '\'')

WebUI.click(boothMediaBtn)

// ---------------------------------------------------------------------------
// 5) The media dialog for THAT booth must open ("<booth> — Media")
// ---------------------------------------------------------------------------
TestObject mediaDialog = new TestObject('boothMediaDialog')

mediaDialog.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\'][contains(., \'' + booth_name + '\')]')

assert WebUI.waitForElementPresent(mediaDialog, 15) : ('Media dialog for booth \'' + booth_name + '\' did not open')

TestObject fileInput = new TestObject('fileInputInMediaDialog')

fileInput.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//input[@type = \'file\']')

assert WebUI.waitForElementPresent(fileInput, 10) : 'File input not present in the media dialog'

// ---------------------------------------------------------------------------
// 6) Upload the FIXED media path (per requirement the path is fixed for now;
//    the 'path' data column is intentionally unused).
//    WebUI.uploadFile() sets the hidden input directly - no native file dialog.
// ---------------------------------------------------------------------------
String mediaPath = 'C:\\Users\\admin\\Desktop\\growtech\\media\\360_F_750002206_j4BMx4eB0ujXBV2G9T28dva0kEYw7q3z.jpg'

WebUI.uploadFile(fileInput, mediaPath)

// the app must stage the file: the "Ready to upload" queue appears in the
// dialog (DOM text is mixed-case; uppercase is CSS text-transform only)
TestObject stagedQueue = new TestObject('stagedUploadQueue')

stagedQueue.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//*[contains(normalize-space(.), \'Ready to upload\')]')

assert WebUI.waitForElementPresent(stagedQueue, 15) : ('File was not staged in the upload queue: ' + mediaPath)

WebUI.comment('File staged for upload: ' + mediaPath)

// ---------------------------------------------------------------------------
// 7) Select PURPOSE from test data (optional - empty column keeps "No purpose")
// ---------------------------------------------------------------------------
String purposeVal = (purpose ?: '').trim()

if (purposeVal) {
    TestObject purposeCombo = new TestObject('purposeCombo')

    purposeCombo.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//*[@aria-autocomplete = \'none\']')

    assert WebUI.waitForElementClickable(purposeCombo, 10) : 'Purpose dropdown not found in the media dialog'

    WebUI.click(purposeCombo)

    // option textContent concatenates label + dimensions with NO whitespace
    // ("Screen 11920 × 1080 px"), so match an exact-text descendant instead
    // (the label span holds exactly "Screen 1"); "Screen1" (no space) does
    // NOT match, so the right option is picked
    TestObject purposeOption = new TestObject('purposeOption')

    purposeOption.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'listbox\']//*[@role = \'option\'][normalize-space(.) = \'' + purposeVal + '\' or .//*[normalize-space(.) = \'' + purposeVal + '\']]')

    assert WebUI.waitForElementClickable(purposeOption, 15) : ('Purpose option \'' + purposeVal + '\' not found in the dropdown')

    WebUI.click(purposeOption)

    WebUI.comment('Purpose selected: ' + purposeVal)
}

// ---------------------------------------------------------------------------
// 8) Click Upload
// ---------------------------------------------------------------------------
TestObject uploadBtn = new TestObject('uploadButton')

uploadBtn.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//button[starts-with(normalize-space(.), \'Upload\')]')

assert WebUI.waitForElementClickable(uploadBtn, 10) : 'Upload button not found in the media dialog'

WebUI.click(uploadBtn)

// ---------------------------------------------------------------------------
// 9) Verify the upload completed: the "Ready to upload" queue must clear.
//    Re-check after a delay in case the app rolls the item back on failure.
// ---------------------------------------------------------------------------
assert WebUI.waitForElementNotPresent(stagedQueue, 30) : 'Upload did not complete - file still in the Ready to upload queue'

WebUI.delay(3)

assert WebUI.waitForElementNotPresent(stagedQueue, 5) : 'Upload rolled back after starting (delivery failed)'

WebUI.comment('Media uploaded to booth ' + booth_name + ' (purpose: ' + (purposeVal ?: 'No purpose') + ')')
