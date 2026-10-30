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
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.TestObject as TestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords as Windows
import internal.GlobalVariable as GlobalVariable
import org.openqa.selenium.Keys as Keys

WebUI.openBrowser(null)

WebUI.navigateToUrl('https://app.msxpo.sa/login')

WebUI.maximizeWindow()

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/a_Join as a Participant'))

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/button_Select Event Host', [(Event_host) : '']))

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/span_radix-_r_k'))

//WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/button_Select Event', [(Event_name) : '']))

//WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/span_radix-_r_1f'))

//'Manage participant/Page_MSxpo - Admin Panel/button_Select Event'

// Select option dynamically
// Get event name from Internal Test Data
String eventName = findTestData('Manage participant/TC_06 join as participant').getValue('event_name', 1)

// Click dropdown
WebUI.click(findTestObject('Manage participant/select event drop down'))

// Create dynamic option locator
TestObject option = new TestObject('dynamic_event_option')

option.addProperty(
    'xpath',
    ConditionType.EQUALS,
    "//*[@role='option' and normalize-space(.)='" + eventName + "']"
)

// Wait for option and select it
WebUI.waitForElementVisible(option, 10)
WebUI.click(option)

WebUI.setText(findTestObject('Manage participant/Page_MSxpo - Admin Panel/input_John Doe'), participant_name)

WebUI.setText(findTestObject('Manage participant/Page_MSxpo - Admin Panel/input_johncompany.com'), participant_email)

WebUI.setText(findTestObject('Manage participant/Page_MSxpo - Admin Panel/input_1-555-0123'), phone)

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/div_Continue'))

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/button_Continue'))

WebUI.setText(findTestObject('Manage participant/Page_MSxpo - Admin Panel/input_Acme Corporation'), company_name)

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/button_Select industry', [(industry) : '']))

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/span_radix-_r_2a'))

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/button_Continue_1'))

WebUI.click(findTestObject('Manage participant/Page_MSxpo - Admin Panel/button_Submit Application'))

