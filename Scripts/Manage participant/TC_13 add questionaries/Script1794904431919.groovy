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
// TC_13 add questionaries  (app.msxpo.sa)
// Data columns bound at suite level:
//   email_address, password, event_name, participant_name,
//   event_questionnaire_title (optional), event_questionnaire_description (optional),
//   questionaries_required, question_1, placeholder,
//   question_1_required, question_1_type
//
// Repaired 2026-11-18 (recorded script was broken):
//   - event selected from test data via the real combobox/option structure
//     (recorded object was the hardcoded 'Virtual trade fair' button plus a
//     positional //*[@dir='auto'] style option - would never follow data)
//   - participant selected by exact name from the list row (recorded button
//     was positional)
//   - Booth Requests tab by exact text (recorded radix id changes every render)
//   - Questionnaire opened via the per-booth icon (title="Questionnaire");
//     the recorded switch/type objects matched the FIRST peer element on the
//     page or a stale radix id, so they could click the wrong control
//   - "Add visitor questionnaire": turned ON only when it is OFF; when it is
//     already ON it is left untouched (requirement)
//   - title and description are OPTIONAL: entered only when the data column
//     has a value, otherwise skipped (recorded script always set them)
//   - "Required" (questionnaire level) and question-level "Required" are set
//     from the data columns questionaries_required / question_1_required
//     (set-if-mismatch, then verified), instead of blind clicks
//   - question type taken from question_1_type via the dialog's combobox
//     (recorded object matched a stale radix id)
//   - question/placeholder inputs matched by placeholder text (recorded
//     objects matched by the CURRENT VALUE, e.g. //*[@value='event questions'],
//     which only works for one pre-filled run)
//   - assertions added at every step, plus persistence verification: after
//     Save closes the dialog, the dialog is reopened and every data-driven
//     value is re-read and compared, then closed with Cancel
//   - login hardened (both prior runs died on flaky maximize/password waits):
//     maximize is now non-fatal and login waits for the fields
//   - APPEND requirement: when the questionnaire already has question(s),
//     +Add question is clicked to append a NEW block at the end; every
//     existing question is kept exactly as it is and ONLY the new (last)
//     block is filled from data. Verification re-reads ALL blocks after save
//     and asserts the count grew by exactly one, the old blocks are unchanged
//     (question/placeholder/type/Required), and the last block matches data
//
// NOTE: data values must not contain an apostrophe (XPath string literal).
// ===========================================================================

// Treat empty / literal "null" data cells as "not provided"
def provided = { Object val ->
	String s = (val == null) ? '' : val.toString().trim()
	return (s && !s.equalsIgnoreCase('null')) ? s : ''
}

// Data column "yes/no" (also accepts true/1/on) -> boolean
def isYes = { Object val ->
	return ['yes', 'true', '1', 'on'].contains(provided(val).toLowerCase())
}

// Ensure a dialog switch ends in the requested state: click only on mismatch
def ensureSwitch = { TestObject sw, boolean want, String label ->
	String state = WebUI.getAttribute(sw, 'data-state')
	assert (state == 'checked' || state == 'unchecked') : ('Switch "' + label + '" not found (data-state=' + state + ')')
	if ((state == 'checked') != want) {
		WebUI.click(sw)
		WebUI.delay(1)
		String after = WebUI.getAttribute(sw, 'data-state')
		assert ((after == 'checked') == want) : ('Could not set switch "' + label + '" to ' + (want ? 'ON' : 'OFF'))
		WebUI.comment('Switch "' + label + '" set to ' + (want ? 'ON' : 'OFF'))
	} else {
		WebUI.comment('Switch "' + label + '" already ' + (want ? 'ON' : 'OFF') + ' - left untouched')
	}
}

// Read the dialog's question state as a Map of Lists (used to snapshot the
// EXISTING questions before appending and to verify them after save):
//   q     - every question input's value, document order (one per block)
//   ph    - every placeholder input's value, document order
//   types - every question type combobox label, document order
//   sw    - every switch data-state: [0] visitor, [1] questionnaire Required,
//           then one Required per question, newest block LAST
//
// This Katalon build has no groovy-json module (JsonSlurper is unavailable),
// so the state travels as count-prefixed, control-character delimited text:
//   <n><SOH>v1<SOH>v2...<STX><n><SOH>...   (SOH = char 1, STX = char 2)
// Those control characters cannot occur in typed UI values, and the count
// prefix distinguishes an empty array from an empty single-element array.
// (WebUI.executeJavaScript in this build also needs the List arg - pass [])
String ARR_SEP = String.valueOf((char) 1)
String GRP_SEP = String.valueOf((char) 2)

def decodeDialogArrays = { String encoded ->
	List<String> groups = encoded.split(GRP_SEP, -1) as List
	return groups.collect { String group ->
		List<String> tokens = group.split(ARR_SEP, -1) as List
		int n = 0
		try {
			n = Integer.parseInt(tokens[0])
		} catch (Exception ignored) {
			n = 0
		}
		return tokens.subList(1, Math.min(1 + n, tokens.size())).toList()
	}
}

def readDialogState = { ->
	String encoded = (WebUI.executeJavaScript('''function e(a){ var s = String.fromCharCode(1); return a.length + s + a.join(s); }
var g = String.fromCharCode(2);
return e([...document.querySelectorAll('[role="dialog"] input[placeholder="e.g. What is your company name?"]')].map(function(i){ return i.value; }))
	+ g + e([...document.querySelectorAll('[role="dialog"] input[placeholder="e.g. Enter your company name here"]')].map(function(i){ return i.value; }))
	+ g + e([...document.querySelectorAll('[role="dialog"] button[role="combobox"]')].map(function(b){ return (b.innerText || "").trim(); }))
	+ g + e([...document.querySelectorAll('[role="dialog"] button[role="switch"]')].map(function(b){ return b.getAttribute("data-state"); }));''', [])) as String
	List<List<String>> arrays = decodeDialogArrays(encoded)
	assert arrays.size() == 4 : ('Could not read the questionnaire dialog state (expected 4 arrays, got ' + arrays.size() + ' from "' + encoded + '")')
	Map state = [q: arrays[0], ph: arrays[1], types: arrays[2], sw: arrays[3]]
	return state
}

// ---------------------------------------------------------------------------
// Selectors (all verified against the live DOM on 2026-11-18; radix ids are
// never referenced - they change on every render)
// ---------------------------------------------------------------------------
TestObject loginEmail = findTestObject('Page_MSxpo - Admin Panel/input_Enter your email')
TestObject loginPassword = findTestObject('Page_MSxpo - Admin Panel/input_Enter your password')
TestObject signInBtn = findTestObject('Page_MSxpo - Admin Panel/button_Sign In')
TestObject messagingLink = findTestObject('Page_MSxpo - Admin Panel/span_Messaging')
TestObject participantsLink = findTestObject('Page_MSxpo - Admin Panel/span_Participants')

// event combobox: the only role=combobox on the Participants page whose text
// is not the rows-per-page selector ("10") - matches placeholder and
// selected states alike
TestObject eventCombo = new TestObject('eventCombo')
eventCombo.addProperty('xpath', ConditionType.EQUALS, '//button[@role = \'combobox\'][not(normalize-space(.) = \'10\')]')

// option: textContent concatenates label + host with NO whitespace
// ("Virtual trade fairFranchise gateway") -> exact or exact-text descendant
TestObject eventOption = new TestObject('eventOption')
eventOption.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'listbox\']//*[@role = \'option\'][normalize-space(.) = \'' + event_name + '\' or .//*[normalize-space(.) = \'' + event_name + '\']]')

// participant row: exact name on the row's name button
TestObject participantBtn = new TestObject('participantRowButton')
participantBtn.addProperty('xpath', ConditionType.EQUALS, '//tbody//button[normalize-space(.) = \'' + participant_name + '\']')

TestObject boothsTab = new TestObject('boothsTab')
boothsTab.addProperty('xpath', ConditionType.EQUALS, '//button[normalize-space(.) = \'Booth Requests\']')

// per-booth Questionnaire icon in the Actions column (data has no booth
// column for this case -> first booth row, matching the recorded flow)
TestObject questionnaireBtn = new TestObject('boothQuestionnaireButton')
questionnaireBtn.addProperty('xpath', ConditionType.EQUALS, '//button[@title = \'Questionnaire\']')

TestObject dialog = new TestObject('configureQuestionnaireDialog')
dialog.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']')

TestObject dialogHeading = new TestObject('configureQuestionnaireHeading')
dialogHeading.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//*[contains(normalize-space(.), \'Configure Questionnaire\')]')

// switches in dialog DOM order (verified): [1] Add visitor questionnaire,
// [2] questionnaire-level Required, [3..] one Required per question (newest
// block LAST) - so the per-question objects below target the NEW (last)
// block that +Add question appends
TestObject visitorSwitch = new TestObject('visitorSwitch')
visitorSwitch.addProperty('xpath', ConditionType.EQUALS, '(//*[@role = \'dialog\']//button[@role = \'switch\'])[1]')

TestObject questionnaireRequiredSwitch = new TestObject('questionnaireRequiredSwitch')
questionnaireRequiredSwitch.addProperty('xpath', ConditionType.EQUALS, '(//*[@role = \'dialog\']//button[@role = \'switch\'])[2]')

// Required switch of the NEWEST (last) question block
TestObject questionRequiredSwitch = new TestObject('questionRequiredSwitch')
questionRequiredSwitch.addProperty('xpath', ConditionType.EQUALS, '(//*[@role = \'dialog\']//button[@role = \'switch\'])[last()]')

TestObject titleInput = new TestObject('questionnaireTitleInput')
titleInput.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//input[@placeholder = \'Questionnaire title (optional)\']')

TestObject descriptionInput = new TestObject('questionnaireDescriptionInput')
descriptionInput.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//textarea[@placeholder = \'Description (optional)\']')

TestObject addQuestionBtn = new TestObject('addQuestionButton')
addQuestionBtn.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//button[normalize-space(.) = \'Add question\']')

// per-question controls of the NEW (last) block - [last()] so the script
// always fills the block that +Add question appended, never an old one
TestObject questionInput = new TestObject('questionTextInput')
questionInput.addProperty('xpath', ConditionType.EQUALS, '(//*[@role = \'dialog\']//input[@placeholder = \'e.g. What is your company name?\'])[last()]')

TestObject placeholderInput = new TestObject('questionPlaceholderInput')
placeholderInput.addProperty('xpath', ConditionType.EQUALS, '(//*[@role = \'dialog\']//input[@placeholder = \'e.g. Enter your company name here\'])[last()]')

TestObject typeCombo = new TestObject('questionTypeCombobox')
typeCombo.addProperty('xpath', ConditionType.EQUALS, '(//*[@role = \'dialog\']//button[@role = \'combobox\'])[last()]')

TestObject typeOption = new TestObject('questionTypeOption')
typeOption.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'listbox\']//*[@role = \'option\'][normalize-space(.) = \'' + question_1_type + '\' or .//*[normalize-space(.) = \'' + question_1_type + '\']]')

TestObject saveBtn = new TestObject('saveQuestionnaireButton')
saveBtn.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//button[.//*[normalize-space(.) = \'Save Questionnaire\'] or normalize-space(.) = \'Save Questionnaire\']')

TestObject cancelBtn = new TestObject('cancelButton')
cancelBtn.addProperty('xpath', ConditionType.EQUALS, '//*[@role = \'dialog\']//button[normalize-space(.) = \'Cancel\']')

// ---------------------------------------------------------------------------
// Values from the data file
// ---------------------------------------------------------------------------
String titleVal = provided(event_questionnaire_title)
String descVal = provided(event_questionnaire_description)
String questionVal = provided(question_1)
String placeholderVal = provided(placeholder)
String typeVal = provided(question_1_type)
boolean questionnaireRequired = isYes(questionaries_required)
boolean questionRequired = isYes(question_1_required)

WebUI.openBrowser(null)

WebUI.navigateToUrl('https://app.msxpo.sa/login')

// maximize is an environment nicety - a failure here killed the previous run
try {
	WebUI.maximizeWindow()
} catch (Exception maximizeError) {
	WebUI.comment('maximizeWindow skipped: ' + maximizeError.message)
}

assert WebUI.waitForElementVisible(loginEmail, 30) : 'Login page did not load - email field not visible'

WebUI.setText(loginEmail, email_address)

WebUI.setText(loginPassword, password)

WebUI.click(signInBtn)

// login guard: the admin sidebar only exists after a successful sign-in
assert WebUI.waitForElementPresent(messagingLink, 30) : 'Login failed - Messaging link not visible after Sign In'

// ---------------------------------------------------------------------------
// Go to the Participants list
// ---------------------------------------------------------------------------
assert WebUI.waitForElementPresent(participantsLink, 30) : 'Participants link not visible in the sidebar'

WebUI.click(participantsLink)

// ---------------------------------------------------------------------------
// 1) Select EVENT from test data
// ---------------------------------------------------------------------------
assert WebUI.waitForElementClickable(eventCombo, 30) : 'Event dropdown not found on the Participants page'

WebUI.click(eventCombo)

assert WebUI.waitForElementClickable(eventOption, 15) : ('Event \'' + event_name + '\' not found in the event dropdown - check the event_name column / account event list')

WebUI.click(eventOption)

// ---------------------------------------------------------------------------
// 2) Select PARTICIPANT from test data - exact text match on the row's name
//    button; clicking it opens the participant detail page
// ---------------------------------------------------------------------------
assert WebUI.waitForElementClickable(participantBtn, 30) : ('Participant \'' + participant_name + '\' not listed for event \'' + event_name + '\'')

WebUI.click(participantBtn)

// ---------------------------------------------------------------------------
// 3) Open the Booth Requests tab
// ---------------------------------------------------------------------------
assert WebUI.waitForElementClickable(boothsTab, 30) : 'Booth Requests tab not found on the participant detail page'

WebUI.click(boothsTab)

// ---------------------------------------------------------------------------
// 4) Open the questionnaire (icon button in the booth row's Actions column)
// ---------------------------------------------------------------------------
assert WebUI.waitForElementClickable(questionnaireBtn, 30) : 'Questionnaire action not found in the booth row (Actions column)'

WebUI.click(questionnaireBtn)

// ---------------------------------------------------------------------------
// 5) The "Configure Questionnaire" dialog must open
// ---------------------------------------------------------------------------
assert WebUI.waitForElementPresent(dialog, 15) : 'Configure Questionnaire dialog did not open'
assert WebUI.waitForElementPresent(dialogHeading, 5) : 'Configure Questionnaire dialog opened but its heading is missing'

// ---------------------------------------------------------------------------
// 6) "Add visitor questionnaire": ON if OFF, left untouched if already ON
// ---------------------------------------------------------------------------
assert WebUI.waitForElementPresent(visitorSwitch, 10) : 'Add visitor questionnaire switch not found in the dialog'

String visitorState = WebUI.getAttribute(visitorSwitch, 'data-state')

if (visitorState == 'checked') {
	WebUI.comment('Add visitor questionnaire already ON - keeping it ON')
} else {
	WebUI.click(visitorSwitch)
	WebUI.delay(1)
	assert WebUI.getAttribute(visitorSwitch, 'data-state') == 'checked' : 'Add visitor questionnaire was OFF and could not be turned ON'
	WebUI.comment('Add visitor questionnaire turned ON')
}

// ---------------------------------------------------------------------------
// 7) Optional questionnaire title - only when provided in the data file
// ---------------------------------------------------------------------------
if (titleVal) {
	assert WebUI.waitForElementPresent(titleInput, 10) : 'Questionnaire title input not found'
	WebUI.setText(titleInput, titleVal)
	WebUI.comment('Title entered: ' + titleVal)
} else {
	WebUI.comment('Title not provided in data - skipped (optional)')
}

// ---------------------------------------------------------------------------
// 8) Optional questionnaire description - only when provided in the data file
// ---------------------------------------------------------------------------
if (descVal) {
	assert WebUI.waitForElementPresent(descriptionInput, 10) : 'Description textarea not found'
	WebUI.setText(descriptionInput, descVal)
	WebUI.comment('Description entered: ' + descVal)
} else {
	WebUI.comment('Description not provided in data - skipped (optional)')
}

// ---------------------------------------------------------------------------
// 9) Questionnaire-level Required switch from data (questionaries_required)
// ---------------------------------------------------------------------------
assert WebUI.waitForElementPresent(questionnaireRequiredSwitch, 10) : 'Questionnaire Required switch not found'

ensureSwitch(questionnaireRequiredSwitch, questionnaireRequired, 'questionnaire Required')

// ---------------------------------------------------------------------------
// 10) APPEND a NEW question block with +Add question
//     - an already-configured question is NEVER edited or removed: the old
//       blocks are snapshotted here and must be byte-for-byte unchanged
//       after save (verified in step 15)
//     - with no question yet, +Add question creates the first block
//       (the empty dialog shows "No questions yet - click Add question")
//     - only the NEW (last) block is filled in steps 11-13
// ---------------------------------------------------------------------------
Map oldState = readDialogState()

List oldQ = (List) oldState.q
List oldPh = (List) oldState.ph
List oldTypes = (List) oldState.types
List oldSw = (List) oldState.sw

int oldBlocks = oldQ.size()

WebUI.comment('Question blocks present before +Add question: ' + oldBlocks + ' -> ' + oldQ)

assert WebUI.waitForElementClickable(addQuestionBtn, 10) : 'Add question button not found in the dialog'

WebUI.click(addQuestionBtn)

int expectedBlocks = oldBlocks + 1

// wait until the new block has actually been appended (React render)
int grown = oldBlocks

for (int i = 0; (i < 10) && (grown < expectedBlocks); i++) {
	WebUI.delay(1)
	grown = (readDialogState().q as List).size()
}

assert grown == expectedBlocks : ('+Add question did not append a new block (blocks before=' + oldBlocks + ', after=' + grown + ')')

WebUI.comment('+Add question appended a NEW block - existing questions kept untouched: ' + oldQ)

assert ((List) readDialogState().q).size() == expectedBlocks : 'Unexpected question block count right after +Add question'

// ---------------------------------------------------------------------------
// 11) Question type from data (question_1_type), e.g. Text / Media - set on
//     the NEW (last) block only
// ---------------------------------------------------------------------------
if (typeVal) {
	assert WebUI.waitForElementClickable(typeCombo, 10) : 'Question type combobox not found'
	WebUI.click(typeCombo)
	assert WebUI.waitForElementClickable(typeOption, 15) : ('Question type option \'' + typeVal + '\' not found in the dropdown')
	WebUI.click(typeOption)
	WebUI.comment('Question type selected: ' + typeVal)
} else {
	WebUI.comment('Question type not provided in data - kept default')
}

// ---------------------------------------------------------------------------
// 12) Question text and placeholder - written into the NEW (last) block only
// ---------------------------------------------------------------------------
if (questionVal) {
	WebUI.setText(questionInput, questionVal)
	WebUI.comment('Question entered: ' + questionVal)
} else {
	WebUI.comment('question_1 not provided in data - left as is')
}

if (placeholderVal) {
	assert WebUI.waitForElementPresent(placeholderInput, 10) : 'Placeholder input not found for the question'
	WebUI.setText(placeholderInput, placeholderVal)
	WebUI.comment('Placeholder entered: ' + placeholderVal)
} else {
	WebUI.comment('Placeholder not provided in data - skipped (optional)')
}

// ---------------------------------------------------------------------------
// 13) Question-level Required switch from data (question_1_required) - the
//     last switch in the dialog belongs to the NEW (last) block
// ---------------------------------------------------------------------------
assert WebUI.waitForElementPresent(questionRequiredSwitch, 10) : 'Question-level Required switch not found'

ensureSwitch(questionRequiredSwitch, questionRequired, 'question Required')

// ---------------------------------------------------------------------------
// 14) Save - success signal: the dialog closes
// ---------------------------------------------------------------------------
assert WebUI.waitForElementClickable(saveBtn, 10) : 'Save Questionnaire button not found'

WebUI.click(saveBtn)

assert WebUI.waitForElementNotPresent(dialog, 15) : 'Save did not complete - Configure Questionnaire dialog is still open'

WebUI.comment('Questionnaire saved - dialog closed')

// ---------------------------------------------------------------------------
// 15) Persistence verification: reopen and re-read every data-driven value,
//     including the APPEND assertions (old blocks untouched, exactly one new)
// ---------------------------------------------------------------------------
WebUI.delay(2)

// Reopening after Save needed resilience: in run 20261118_163602 the
// Questionnaire button stayed unclickable for 15s after a successful Save
// while manual reproduction looked normal. The reopen therefore:
//   - logs a full page-state snapshot on every attempt (URL, tab states,
//     button presence/enabled/visibility, overlays, table rows)
//   - re-activates the Booth Requests tab if a re-render reset it
//   - retries up to 7 times before failing (the not-clickable window after
//     Save measured ~29s in run 20261118_165631; 7 attempts cover ~70s)
// Run 20261118_182554 then showed a PERSISTENT failure after Save: Katalon
// logged "Element is detached from the DOM while waiting interactable" and
// "Waiting for element timed out" while the DOM snapshot still showed the
// button present and enabled (React re-renders the booth row after Save).
// So each attempt now also:
//   - scrolls the button into view first (non-fatal)
//   - records the button's computed state (size/visibility/hidden ancestors/
//     what covers its center) when the normal click is not possible
//   - falls back to dispatching the real mouse events on the button via
//     JavaScript, which works even while the row is mid re-render
String btnStateJs = '''var b = document.querySelector('button[title="Questionnaire"]');
if (!b) { return JSON.stringify({found: false, url: location.href, dialog: !!document.querySelector('[role=dialog]')}); }
var r = b.getBoundingClientRect();
var cs = getComputedStyle(b);
var hidden = [];
var n = b.parentElement;
while (n && n !== document.documentElement) {
	var s = getComputedStyle(n);
	if (s.visibility !== 'visible' || s.display === 'none') {
		hidden.push(n.tagName + '{display:' + s.display + ',visibility:' + s.visibility + ',opacity:' + s.opacity + '}');
	}
	n = n.parentElement;
}
var top = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
return JSON.stringify({
	rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)],
	viewport: [innerWidth, innerHeight],
	display: cs.display, visibility: cs.visibility, opacity: cs.opacity,
	disabled: b.disabled, offsetParent: b.offsetParent ? b.offsetParent.tagName : 'NULL',
	hiddenAncestors: hidden,
	centerTop: top ? (b.contains(top) ? 'SELF_OR_DESC' : top.tagName + '.' + String(top.className).slice(0, 60)) : 'NULL_POINT',
	dialog: !!document.querySelector('[role=dialog]')
});'''

String btnClickJs = '''var b = document.querySelector('button[title="Questionnaire"]');
if (!b) { return 'button not found (url=' + location.href + ')'; }
['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(function(t) {
	b.dispatchEvent(new MouseEvent(t, {bubbles: true, cancelable: true, view: window}));
});
return 'mouse events dispatched on the Questionnaire button';'''

boolean dialogReopened = false

for (int attempt = 1; (attempt <= 7) && !dialogReopened; attempt++) {
	String snapshot = (WebUI.executeJavaScript('''return JSON.stringify({url: location.href, dialog: !!document.querySelector('[role=dialog]'), backdrops: document.querySelectorAll('[class*="fixed inset-0"]').length, tabs: [...document.querySelectorAll('[id*=trigger]')].map(function(e){ return (e.innerText||'').trim() + ':' + e.getAttribute('data-state'); }), qbtn: (function(){ var b = document.querySelector('button[title="Questionnaire"]'); return b ? {count: document.querySelectorAll('button[title="Questionnaire"]').length, disabled: b.disabled, visible: b.offsetParent !== null} : null; })(), rows: document.querySelectorAll('table tr').length, bodyStart: document.body.innerText.slice(0, 120)});''', [])) as String
	WebUI.comment('Reopen attempt ' + attempt + ' page state: ' + snapshot)

	// recovery: if the Booth Requests tab was reset by the post-save
	// re-render, the Questionnaire button does not exist - click it again
	String tabState = (WebUI.executeJavaScript('var t = document.querySelector("[id*=trigger-booths]"); return t ? t.getAttribute("data-state") : "absent";', [])) as String

	if (tabState != 'active') {
		WebUI.comment('Booth Requests tab state is "' + tabState + '" after save - clicking it again')
		WebUI.click(boothsTab)
		WebUI.delay(1)
	}

	// bring the button into view (the post-save re-render can leave the
	// page scrolled); non-fatal if the row is briefly detached
	try {
		WebUI.scrollToElement(questionnaireBtn)
	} catch (Exception scrollError) {
		WebUI.comment('scrollToElement(Questionnaire) skipped: ' + scrollError.message)
	}

	boolean katalonClicked = false

	if (WebUI.waitForElementClickable(questionnaireBtn, 6, FailureHandling.OPTIONAL)) {
		try {
			WebUI.click(questionnaireBtn)
			katalonClicked = true
		} catch (Exception clickError) {
			WebUI.comment('Katalon click on the Questionnaire button failed: ' + clickError.message)
		}
	} else {
		// record WHY the button is not interactable (detached / hidden / size)
		String btnState = (WebUI.executeJavaScript(btnStateJs, [])) as String
		WebUI.comment('Questionnaire button not interactable - computed state: ' + btnState)
	}

	if (katalonClicked) {
		dialogReopened = WebUI.waitForElementPresent(dialog, 10, FailureHandling.OPTIONAL)
	}

	// JS fallback when the normal click was not possible (button detached or
	// covered - run 20261118_184840 showed a saved-toast overlaying it) or
	// when the click did not open the dialog (it may have hit the overlay):
	// dispatch the real mouse events directly on the button
	if (!dialogReopened) {
		String jsClickResult = (WebUI.executeJavaScript(btnClickJs, [])) as String
		WebUI.comment('JS fallback click: ' + jsClickResult)
		dialogReopened = WebUI.waitForElementPresent(dialog, 10, FailureHandling.OPTIONAL)
	}

	if (!dialogReopened) {
		WebUI.delay(2)
	}
}

assert dialogReopened : 'Could not reopen the questionnaire dialog for verification (see "Reopen attempt" comments for the page state)'

// switches reflect the saved state
assert WebUI.getAttribute(visitorSwitch, 'data-state') == 'checked' : 'Verification failed: Add visitor questionnaire is not ON after save'

assert ((WebUI.getAttribute(questionnaireRequiredSwitch, 'data-state') == 'checked') == questionnaireRequired) : ('Verification failed: questionnaire Required should be ' + (questionnaireRequired ? 'ON' : 'OFF') + ' after save')

// questionRequiredSwitch targets the LAST switch = the NEW question block
assert ((WebUI.getAttribute(questionRequiredSwitch, 'data-state') == 'checked') == questionRequired) : ('Verification failed: the NEW question\'s Required switch should be ' + (questionRequired ? 'ON' : 'OFF') + ' after save')

// text values as reloaded from the saved questionnaire
if (titleVal) {
	String savedTitle = WebUI.getAttribute(titleInput, 'value')
	assert savedTitle == titleVal : ('Verification failed: title is "' + savedTitle + '" but data expects "' + titleVal + '"')
	WebUI.comment('Verified title: ' + savedTitle)
}

if (descVal) {
	// textarea has no value attribute - read the live DOM property instead
	// (WebUI.findElement does not exist in this Katalon build, so the element
	// is resolved inside the JavaScript itself)
	String savedDesc = (WebUI.executeJavaScript('return (document.evaluate("//textarea[@placeholder=\\"Description (optional)\\"]", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue || {}).value;', [])) as String
	assert savedDesc == descVal : ('Verification failed: description is "' + savedDesc + '" but data expects "' + descVal + '"')
	WebUI.comment('Verified description: ' + savedDesc)
}

// --- APPEND verification: re-read ALL blocks and assert -------------------
//     1) exactly one block was added (count = old + 1)
//     2) every pre-existing block is byte-for-byte unchanged
//     3) the NEW (last) block matches the data file
Map savedState = readDialogState()

List savedQ = (List) savedState.q
List savedPh = (List) savedState.ph
List savedTypes = (List) savedState.types
List savedSw = (List) savedState.sw

assert savedQ.size() == expectedBlocks : ('Verification failed: expected ' + expectedBlocks + ' question block(s) after save but found ' + savedQ.size())

assert savedSw.size() == (2 + expectedBlocks) : ('Verification failed: expected ' + (2 + expectedBlocks) + ' switches (2 header + ' + expectedBlocks + ' question) but found ' + savedSw.size())

WebUI.comment('Verified block count after save: ' + savedQ.size() + ' (was ' + oldBlocks + ' before +Add question)')

int lastIdx = expectedBlocks - 1

// 2) old blocks untouched (question, placeholder, type, Required switch)
for (int i = 0; i < oldBlocks; i++) {
	assert savedQ[i] == oldQ[i] : ('Verification failed: existing question ' + (i + 1) + ' was modified by the append (was "' + oldQ[i] + '", now "' + savedQ[i] + '")')
	assert savedPh[i] == oldPh[i] : ('Verification failed: existing placeholder ' + (i + 1) + ' was modified by the append (was "' + oldPh[i] + '", now "' + savedPh[i] + '")')
	assert savedTypes[i] == oldTypes[i] : ('Verification failed: existing question type ' + (i + 1) + ' was modified by the append (was "' + oldTypes[i] + '", now "' + savedTypes[i] + '")')
	assert savedSw[2 + i] == oldSw[2 + i] : ('Verification failed: existing Required switch ' + (i + 1) + ' was modified by the append (was "' + oldSw[2 + i] + '", now "' + savedSw[2 + i] + '")')
}

if (oldBlocks > 0) {
	WebUI.comment('Verified: all ' + oldBlocks + ' pre-existing question(s) kept unchanged after the append')
} else {
	WebUI.comment('No question existed before - the first block was created by +Add question')
}

// 3) the NEW (last) block matches the data file
assert savedQ[lastIdx] == questionVal : ('Verification failed: new question is "' + savedQ[lastIdx] + '" but data expects "' + questionVal + '"')
WebUI.comment('Verified new (appended) question: ' + savedQ[lastIdx])

assert savedPh[lastIdx] == placeholderVal : ('Verification failed: new placeholder is "' + savedPh[lastIdx] + '" but data expects "' + placeholderVal + '"')
WebUI.comment('Verified new (appended) placeholder: ' + savedPh[lastIdx])

if (typeVal) {
	assert savedTypes[lastIdx] == typeVal : ('Verification failed: new question type is "' + savedTypes[lastIdx] + '" but data expects "' + typeVal + '"')
	WebUI.comment('Verified new (appended) question type: ' + savedTypes[lastIdx])
}

// close the reopened dialog without changing anything
assert WebUI.waitForElementClickable(cancelBtn, 10) : 'Cancel button not found to close the verification dialog'

WebUI.click(cancelBtn)

assert WebUI.waitForElementNotPresent(dialog, 10) : 'Dialog did not close after verification'

WebUI.comment('TC_13 completed: questionnaire saved for booth questionnaire on event \'' + event_name + '\', participant \'' + participant_name + '\'')
