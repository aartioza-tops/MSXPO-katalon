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

WebUI.navigateToUrl('https://demo.msxpo.sa/')

WebUI.maximizeWindow()

WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/a_Sign In'))

WebUI.setText(findTestObject('Page_MSxpo  Virtual Expo Platform/input_Enter your email address'), 'aartioza2026+vip@gmail.com')

WebUI.setText(findTestObject('Page_MSxpo  Virtual Expo Platform/input_Enter your password'), 'Admin@123')

WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/span_Sign In'))

WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/button_Go to Dashboard'))

// WebUI.click(findTestObject('Manage visitor/event drop down'))

String chosenEvent = "Test 11" //event_name

// 1. open the event dropdown (chevron trigger in the header)
TestObject dropTrigger = findTestObject('Manage visitor/event drop down')
assert WebUI.waitForElementPresent(dropTrigger, 15) : "Event dropdown trigger not found in the header"
WebUI.click(dropTrigger)

// 2. click the option matching the data-file value
TestObject eventOption = findTestObject('Manage visitor/event option', [('event_name') : chosenEvent])
assert WebUI.waitForElementPresent(eventOption, 10) : "Event option '${chosenEvent}' not found in the dropdown list"
WebUI.click(eventOption)

// 3. panel closes and the trigger then shows the chosen event
assert WebUI.waitForElementNotPresent(eventOption, 5) : "Dropdown stayed open after selecting '${chosenEvent}'"
TestObject chosenTrigger = findTestObject('Manage visitor/event trigger shows chosen', [('event_name') : chosenEvent])
assert WebUI.waitForElementPresent(chosenTrigger, 5) : "Header does not show '${chosenEvent}' after selection"
WebUI.comment("Selected event from data file: ${chosenEvent}")


WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/button_Enter Expo Hall'))

//WebUI.click(findTestObject('Page_MSxpo  Virtual Expo Platform/button_Enter Expo Hall'))

//TestObject unityCanvas = findTestObject('Page_/canvas_react-unity-webgl-canvas-0')

// TestObject unitySpinner = findTestObject('Manage visitor/unity loading spinner')

WebUI.click(findTestObject('Page_/canvas_react-unity-webgl-canvas-0'))

WebUI.click(findTestObject('Page_Test 11  MSxpo/svg_lucide lucide-menu w-5 h-5'))

WebUI.click(findTestObject('Page_Test 11  MSxpo/button_Visit Booth'))

WebUI.click(findTestObject('Page_Test 11  MSxpo/button_Chat with Staff'))

WebUI.setText(findTestObject('Page_Test 11  MSxpo/textarea_Hi Id like to learn more about your p'), 'h')

WebUI.click(findTestObject('Page_Test 11  MSxpo/button_Send Chat Request'))

WebUI.click(findTestObject('Page_Test 11  MSxpo/div_fixed inset-0 z-50 bg-black_80 data-stateo'))

WebUI.click(findTestObject('Page_Test 11  MSxpo/button_Go to the dashboard'))




