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
import com.kms.katalon.core.testobject.ConditionType as ConditionType
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords as Windows
import internal.GlobalVariable as GlobalVariable
import org.openqa.selenium.Keys as Keys

// ---------------------------------------------------------------------------
// TC_09 - Set meeting availability
// Data source: Data Files / Manage participant / TC_09 set meeting availiblity
// Columns: email_address, password, sun, mon, tue, web, thu, fri, sat,
//          duration, timezone
//   day value "unavailable"        -> day toggle stays/becomes OFF
//   day value "9 am to 5 pm"       -> toggle ON + first slot set to that range
//   duration "1 hour"              -> Meeting Duration chip
//   timezone "Kolkata,Mumbai,..."  -> first city is used to pick the zone
// ---------------------------------------------------------------------------

// build a TestObject on the fly (no Object Repository entries needed)
def obj = { String xpath ->
	TestObject to = new TestObject()
	to.addProperty('xpath', ConditionType.EQUALS, xpath)
	return to
}

// "9" + "am"  ->  "9:00 AM"   (format used by the slot dropdown options)
def normaliseTime = { String t, String ap ->
	String[] parts = t.split(':')
	String hh = parts[0]
	String mm = (parts.length > 1) ? parts[1] : '00'
	return hh + ':' + mm + ' ' + ap.toUpperCase()
}

def df = findTestData('Manage participant/TC_09 set meeting availiblity')

// row 1 of this file holds the data (row 2 is an all-null placeholder row)
String email_address = df.getValue('email_address', 1).trim()
String password      = df.getValue('password', 1).trim()
String duration      = df.getValue('duration', 1).trim()
String timezone      = df.getValue('timezone', 1).trim()

// day column -> day label shown in the UI (note: data column "web" = Wednesday)
def dayColumns = [sun: 'Sun', mon: 'Mon', tue: 'Tue', web: 'Wed', thu: 'Thu', fri: 'Fri', sat: 'Sat']

// --- 1. login ---------------------------------------------------------------
WebUI.openBrowser(null)
WebUI.navigateToUrl('https://app.msxpo.sa/')
WebUI.maximizeWindow()

WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your email'), email_address)
WebUI.setText(findTestObject('Page_MSxpo - Admin Panel/input_Enter your password'), password)
WebUI.click(findTestObject('Page_MSxpo - Admin Panel/button_Sign In'))
WebUI.waitForElementPresent(obj("//a[normalize-space()='Meetings']"), 15)

// --- 2. Meetings tab --------------------------------------------------------
WebUI.click(obj("//a[normalize-space()='Meetings']"))
WebUI.waitForElementPresent(obj("//button[normalize-space()='Edit']"), 15)

// --- 3. open the calendar setup (Edit) -------------------------------------
WebUI.click(obj("//button[normalize-space()='Edit']"))
assert WebUI.waitForElementPresent(obj("//*[normalize-space()='Edit Calendar Settings']"), 10) : \
	"Edit Calendar Settings panel did not open"

// --- 4. availability per day, from the data file ----------------------------
dayColumns.each { col, label ->
	String value = df.getValue(col, 1).trim()
	String rowXp = "//span[normalize-space()='" + label + "']/ancestor::div[contains(@class,'rounded-lg')][1]"
	TestObject toggle    = obj("//span[normalize-space()='" + label + "']/preceding-sibling::button[1]")
	TestObject statusOff = obj(rowXp + "//p[normalize-space()='Unavailable']")

	WebUI.waitForElementPresent(toggle, 10)
	boolean isOn = WebUI.getAttribute(toggle, 'class').contains('0091EA') // blue = day ON

	if (value.equalsIgnoreCase('unavailable')) {
		if (isOn) {
			WebUI.click(toggle)
		}
		assert WebUI.waitForElementPresent(statusOff, 5) : label + " did not become Unavailable"
		WebUI.comment(label + " = Unavailable (from data file)")
	} else {
		if (!isOn) {
			WebUI.click(toggle)
		}
		assert WebUI.waitForElementNotPresent(statusOff, 5) : label + " did not become Available"

		// value looks like "9 am to 5 pm" -> set the first slot start/end
		def m = (value =~ /(?i)(\d{1,2}(?::\d{2})?)\s*(am|pm)\s*to\s*(\d{1,2}(?::\d{2})?)\s*(am|pm)/)
		if (m.find()) {
			String startTxt = normaliseTime(m[0][1], m[0][2])
			String endTxt   = normaliseTime(m[0][3], m[0][4])
			TestObject startCombo = obj(rowXp + "//button[@role='combobox'][1]")
			TestObject endCombo   = obj(rowXp + "//button[@role='combobox'][2]")
			assert WebUI.waitForElementPresent(startCombo, 5) : label + " time slot did not appear"
			WebUI.click(startCombo)
			WebUI.click(obj("//div[@role='option'][normalize-space()='" + startTxt + "']"))
			WebUI.click(endCombo)
			WebUI.click(obj("//div[@role='option'][normalize-space()='" + endTxt + "']"))
			WebUI.comment(label + " = " + startTxt + " to " + endTxt + " (from data file)")
		} else {
			WebUI.comment(label + ": '" + value + "' is not a time range - day toggled ON only")
		}
	}
}

// --- 5. meeting duration, from the data file --------------------------------
TestObject durBtn = obj("//*[normalize-space()='Meeting Duration']/following::button[normalize-space()='" + duration + "'][1]")
assert WebUI.waitForElementPresent(durBtn, 5) : "Duration option '" + duration + "' not found"
if (!WebUI.getAttribute(durBtn, 'class').contains('0091EA')) {
	WebUI.click(durBtn)
}
WebUI.comment("Meeting duration = " + duration)

// --- 6. timezone, from the data file ---------------------------------------
String tzKey = timezone.split(',')[0].trim()   // "Kolkata,Mumbai,..." -> "Kolkata"
TestObject tzCombo = obj("//*[normalize-space()='Timezone']/following::button[@role='combobox'][1]")
WebUI.click(tzCombo)
TestObject tzOpt = obj("//div[@role='option'][contains(., '" + tzKey + "')]")
assert WebUI.waitForElementPresent(tzOpt, 5) : "Timezone option containing '" + tzKey + "' not found"
WebUI.click(tzOpt)
WebUI.comment("Timezone = " + timezone)

// --- 7. update settings -----------------------------------------------------
WebUI.click(obj("//button[normalize-space()='Update Settings']"))
assert WebUI.waitForElementPresent(obj("//button[normalize-space()='Edit']"), 10) : \
	"Settings did not save - the Edit button did not come back"
assert WebUI.waitForElementNotPresent(obj("//*[normalize-space()='Edit Calendar Settings']"), 10) : \
	"Edit Calendar Settings panel did not close after saving"
WebUI.comment("Meeting availability settings updated from data file 'TC_09 set meeting availiblity'")

WebUI.closeBrowser()
