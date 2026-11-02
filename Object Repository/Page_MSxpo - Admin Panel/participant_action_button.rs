<?xml version="1.0" encoding="UTF-8"?>
<WebElementEntity>
   <description>Approve/Reject button INSIDE the row of participant ${participant_name} (action = Approve | Reject). Row-scoped so the button can only ever be clicked for the given participant.</description>
   <name>participant_action_button</name>
   <tag></tag>
   <elementGuidId>8fb98696-33e4-4861-aa98-eadd45c66748</elementGuidId>
   <origin>STUDIOASSIST</origin>
   <selectorCollection>
      <entry>
         <key>XPATH</key>
         <value>//table//tr[.//td[.//button[normalize-space()='${participant_name}']]]//button[@aria-label='${action}']</value>
      </entry>
   </selectorCollection>
   <selectorMethod>XPATH</selectorMethod>
   <smartLocatorEnabled>false</smartLocatorEnabled>
   <useRalativeImagePath>false</useRalativeImagePath>
</WebElementEntity>
