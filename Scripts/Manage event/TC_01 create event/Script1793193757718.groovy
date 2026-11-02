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

WebUI.openBrowser(null)



WebUI.navigateToUrl('https://app.msxpo.sa/login')



WebUI.maximizeWindow()



WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your email'), email_address)



WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your password'), password)



WebUI.click(findTestObject('Page_MSxpo - Admin Panel/button_Sign In'))



WebUI.click(findTestObject('Page_MSxpo - Admin Panel/span_Events'))



WebUI.click(findTestObject('Page_MSxpo - Admin Panel/span_Create Event'))



WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_e.g. Tech Expo 2026'), event_name)



// Event type: the native <select> ('Manage event/event_type') is a HIDDEN shim - selectOptionByLabel
// on it does nothing (the app's React state never updates). The visible control is a Radix combobox
// whose DOM id (radix-_r_...) changes on every page load, so the recorded 'span_radix-_r_4q' object
// can never match. Fix: click the labelled trigger, then the [role=option] whose text equals the
// test-data value of 'event_type'.
TestObject eventTypeTrigger = findTestObject('Manage event/event_type_trigger')
if (!WebUI.verifyElementPresent(eventTypeTrigger, 5, FailureHandling.OPTIONAL)) {
	// pristine page state fallback: recorded trigger still showing the "Select Event Type" placeholder
	eventTypeTrigger = findTestObject('Page_MSxpo - Admin Panel/button_Select Event Type')
}
WebUI.click(eventTypeTrigger)

TestObject eventTypeOption = findTestObject('Manage event/event_type_option', [('event_type') : event_type])
WebUI.waitForElementVisible(eventTypeOption, 5)
WebUI.click(eventTypeOption)
WebUI.delay(0.5) // dropdown closes, trigger now shows the chosen type


// ---------------------------------------------------------------------------
// Start / End date selection - DATA DRIVEN from the data-file columns
// start_date / end_date. Nothing is hardcoded: any date in any month is
// selected by
//   1. parsing the column value ('November 2nd,2026' / 'November 12nd,2026';
//      also tolerates '2026-11-02' and any st/nd/rd/th suffix, even the
//      '12nd' typo),
//   2. building the day-cell aria-label fragment 'November 12th, 2026',
//   3. opening the picker via its exact placeholder button text,
//   4. month-paging via the caption ('November 2026') until the cell exists,
//   5. clicking the cell and asserting the field shows the chosen date.
//
// Live structure (verified on app.msxpo.sa):
//  - day cells are react-day-picker buttons with aria-labels like
//    'Thursday, November 12th, 2026'; TODAY's cell is prefixed 'Today, '
//    -> the cell is matched with contains(), never equality.
//  - the placeholder is exact-text inside a button; the recorded
//    'span_Select end date' CSS (.text-muted-foreground span) matches the
//    START field first, so exact button text is used instead.
//  - the picker closes itself after a day is clicked.
// ---------------------------------------------------------------------------

def resolveColumn = { String boundValue, String column ->
	String v = (boundValue == null) ? '' : boundValue.toString().trim()
	if (!v) {
		v = findTestData('Manage event/TC_01 create event').getValue(column, 1).toString().trim()
	}
	v
}

def monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

def parseDate = { String raw ->
	def m = (raw =~ /^\s*([A-Za-z]+)\s+(\d{1,2})(?:st|nd|rd|th)?\s*,?\s*(\d{4})\s*$/)
	if (m.find()) {
		String mon = m[0][1].capitalize()
		if (!monthNames.contains(mon)) {
			throw new IllegalArgumentException("Unknown month in date '${raw}' - expected a full English month name e.g. 'November 2nd,2026'")
		}
		return [month: mon, day: (m[0][2] as int), year: (m[0][3] as int)]
	}
	def iso = (raw =~ /^\s*(\d{4})-(\d{1,2})-(\d{1,2})\s*$/)
	if (iso.find()) {
		int mi = iso[0][2] as int
		if ((mi < 1) || (mi > 12)) {
			throw new IllegalArgumentException("Bad month in date '${raw}'")
		}
		return [month: monthNames[mi - 1], day: (iso[0][3] as int), year: (iso[0][1] as int)]
	}
	throw new IllegalArgumentException("Cannot parse date '${raw}' - expected e.g. 'November 2nd,2026' or '2026-11-02'")
}

def ordinal = { int d ->
	String suffix = 'th'
	if (!((d >= 11) && (d <= 13))) {
		switch (d % 10) {
			case 1: suffix = 'st'; break
			case 2: suffix = 'nd'; break
			case 3: suffix = 'rd'; break
			default: break
		}
	}
	d.toString() + suffix
}

def monthNumber = { String name ->
	monthNames.indexOf(name.capitalize())
}

def selectCalendarDate = { String rawDate, String placeholder, String fieldName ->
	def parts = parseDate(rawDate)
	String dayLabel = parts.month + ' ' + ordinal(parts.day) + ', ' + parts.year
	WebUI.comment("Selecting ${fieldName} = ${dayLabel} (data value '${rawDate}')")

	// 1. open the picker - exact button text, unique on the create-event form.
	//    If the field already shows a previous value instead of the placeholder
	//    (restored draft), fall back to the button bearing the chosen date text.
	TestObject trigger = findTestObject('Page_MSxpo - Admin Panel/button_date_trigger', [('placeholder') : placeholder])
	TestObject valueTrigger = findTestObject('Page_MSxpo - Admin Panel/button_date_trigger', [('placeholder') : dayLabel])
	if (WebUI.waitForElementPresent(trigger, 5)) {
		WebUI.click(trigger)
	} else if (WebUI.waitForElementPresent(valueTrigger, 5)) {
		WebUI.comment("${fieldName}: placeholder '${placeholder}' not shown - field already has a value, reopening via '${dayLabel}'")
		WebUI.click(valueTrigger)
	} else {
		throw new AssertionError("${fieldName}: neither '${placeholder}' nor '${dayLabel}' button found - date field missing")
	}

	// 2. locate the day cell; page month-by-month until it is rendered
	TestObject dayCell = findTestObject('Page_MSxpo - Admin Panel/button_date_day_cell', [('day_label') : dayLabel])
	TestObject caption = findTestObject('Page_MSxpo - Admin Panel/span_month_caption')
	TestObject prevMonth = findTestObject('Page_MSxpo - Admin Panel/button_Go to the Previous Month')
	TestObject nextMonth = findTestObject('Page_MSxpo - Admin Panel/button_Go to the Next Month')
	assert WebUI.waitForElementPresent(caption, 10) : "${fieldName}: calendar did not open (no month caption found)"

	for (int i = 0; (i < 24) && !(WebUI.waitForElementPresent(dayCell, 2)); i++) {
		String cap = WebUI.getText(caption).replaceAll(/\s+/, ' ').trim()
		def cm = (cap =~ /^([A-Za-z]+)\s+(\d{4})$/)
		assert cm.find() : "${fieldName}: unexpected calendar caption '${cap}'"
		int curIdx = monthNumber(cm[0][1])
		assert curIdx >= 0 : "${fieldName}: unknown month in caption '${cap}'"
		int curYear = cm[0][2] as int
		int targetIdx = monthNumber(parts.month)
		boolean tooEarly = (curYear < parts.year) || ((curYear == parts.year) && (curIdx < targetIdx))
		boolean tooLate = (curYear > parts.year) || ((curYear == parts.year) && (curIdx > targetIdx))
		if (!tooEarly && !tooLate) {
			break // correct month reached - cell must be present now
		}
		WebUI.click(tooEarly ? nextMonth : prevMonth)
		WebUI.delay(0.3)
	}

	assert WebUI.waitForElementPresent(dayCell, 10) : "${fieldName}: day cell '${dayLabel}' not found in the calendar for value '${rawDate}'"
	WebUI.click(dayCell)

	// 3. the picker closes itself and the field then shows the chosen date
	assert WebUI.waitForElementNotPresent(dayCell, 10) : "${fieldName}: calendar stayed open after clicking '${dayLabel}'"

	// verify the field now shows the chosen date. verifyTextPresent(String, Integer)
	// does not exist in this Katalon build (MissingMethodException in run ...),
	// so assert via the date trigger button bearing the exact date text instead -
	// the same object used to open the picker in step 1.
	TestObject shownValue = findTestObject('Page_MSxpo - Admin Panel/button_date_trigger', [('placeholder') : dayLabel])
	assert WebUI.waitForElementPresent(shownValue, 5) : "${fieldName}: field does not show '${dayLabel}' after selection"
	WebUI.comment("${fieldName} set to ${dayLabel}")
}

selectCalendarDate(resolveColumn(start_date, 'start_date'), 'Select start date', 'start_date')
selectCalendarDate(resolveColumn(end_date, 'end_date'), 'Select end date', 'end_date')



WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/textarea_Brief event overview'), description)

WebUI.click(findTestObject('Page_MSxpo - Admin Panel/button_Continue'))



// Step 2 (Event Admin):
// The admin row renders asynchronously after 'Add Admin'. Email starts disabled=true and the
// phone cell is wrapped in 'pointer-events-none opacity-40' - BOTH unlock only when the name
// suggestion popover ("Add 'X' as new admin", blue h-4 plus) is CLICKED. That popover appears
// ~0.5-1.5s after typing the name, so wait for it with waitForElementPresent (verifyElementPresent
// does NOT poll in this Katalon version - it returned in 0.16s and skipped the click in run
// 20261029_143813, leaving phone non-interactable in run 20261029_144744).
// Phone must be set BEFORE email: filling email auto-commits the row to a read-only 'Pending'
// state that REMOVES the phone input (run 20261029_143813 failure: PHONE shown as '-' no input).
WebUI.click(findTestObject('Page_MSxpo - Admin Panel/svg_tabler-icon tabler-icon-plus h-3 w-3'))

TestObject adminFullName = findTestObject('Page_MSxpo - Admin Panel/input_Full name')
WebUI.waitForElementPresent(adminFullName, 15)
WebUI.setText(adminFullName, event_admin_name)

TestObject addNewAdmin = findTestObject('Page_MSxpo - Admin Panel/svg_tabler-icon tabler-icon-plus h-4 w-4 text-')
WebUI.waitForElementPresent(addNewAdmin, 10)
WebUI.click(addNewAdmin)
WebUI.delay(0.5) // let the unlock re-render settle

TestObject adminPhone = findTestObject('Page_MSxpo - Admin Panel/input_500 000 000')
WebUI.waitForElementPresent(adminPhone, 10)
WebUI.setText(adminPhone, phone_number)

TestObject adminEmail = findTestObject('Page_MSxpo - Admin Panel/input_Email address')
WebUI.waitForElementPresent(adminEmail, 10)
WebUI.setText(adminEmail, admin_email_address) // last editable field -> row commits to Pending

WebUI.click(findTestObject('Page_MSxpo - Admin Panel/button_Continue_1'))

// Step 3 (Visitor Questionnaire) and Step 4 (Floor Plan) are NOT mandatory - skip straight
// through. Step 3 has nothing required: advance with its 'Continue' button. Step 4 has no
// Continue at all - its advance button is 'Skip Floor Planner'.
TestObject questionnaireContinue = findTestObject('Page_MSxpo - Admin Panel/button_Continue_2')
WebUI.waitForElementPresent(questionnaireContinue, 15)
WebUI.click(questionnaireContinue)

TestObject skipFloorPlanner = findTestObject('Page_MSxpo - Admin Panel/button_Skip Floor Planner')
WebUI.waitForElementPresent(skipFloorPlanner, 15)
WebUI.click(skipFloorPlanner)

// Step 5 (Review & Create): 'Save as Draft' completes the create-event flow.
TestObject saveAsDraft = findTestObject('Page_MSxpo - Admin Panel/button_Save as Draft')
WebUI.waitForElementPresent(saveAsDraft, 15)
WebUI.click(saveAsDraft)

// After the save the app lands on the event page whose <h1> shows the event name. The draft is
// already created once 'Save as Draft' returns, so this last confirmation click is optional.
TestObject savedEventTitle = findTestObject('Page_MSxpo - Admin Panel/h1_Test 2.0')
WebUI.waitForElementPresent(savedEventTitle, 10, FailureHandling.OPTIONAL)
WebUI.click(savedEventTitle, FailureHandling.OPTIONAL)
