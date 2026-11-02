<?xml version="1.0" encoding="UTF-8"?>
<WebElementEntity>
   <description>Approve/Reject confirmation dialog that mentions participant ${participant_name} (e.g. 'Are you sure you want to approve test case 06?'). Matching by name proves the dialog targets the right participant.</description>
   <name>confirm_dialog</name>
   <tag></tag>
   <elementGuidId>bec4136e-4f3d-41ba-bae3-e2dcc75c6a5d</elementGuidId>
   <origin>STUDIOASSIST</origin>
   <selectorCollection>
      <entry>
         <key>XPATH</key>
         <value>//div[@role='dialog'][contains(normalize-space(.), '${participant_name}')]</value>
      </entry>
   </selectorCollection>
   <selectorMethod>XPATH</selectorMethod>
   <smartLocatorEnabled>false</smartLocatorEnabled>
   <useRalativeImagePath>false</useRalativeImagePath>
</WebElementEntity>
