# JavaRecursiveGoogleDrive
A tool developed using JAVA & Google Drive API to share or un-share files recursively on GoogleDrive.

To setup the tool, you have to create a Google Drive API key and download it in json format, then rename the file to 
'client_secret.json' and place it under 'resources' folder.

These steps are described here : https://developers.google.com/drive/v3/web/quickstart/java , Section 'Step 1: Turn on the Drive API'

If you won't browse to the link above, here is the instructions (given by Google) :

Step 1: Turn on the Drive API

<ol>
<li>Use
   <a href="https://console.developers.google.com/start/api?id=drive" target="_blank">this wizard</a>
   to create or select a project in the Google Developers Console and
   automatically turn on the API. Click <strong>Continue</strong>, then <strong>Go to credentials</strong>.</li>
<li>On the <strong>Add credentials to your project</strong> page, click the <strong>Cancel</strong> button.</li>
<li>At the top of the page, select the <strong>OAuth consent screen</strong> tab. Select an
   <strong>Email address</strong>, enter a <strong>Product name</strong> if not already set, and click the
   <strong>Save</strong> button.
</li>
<li>Select the <strong>Credentials</strong> tab, click the <strong>Create credentials</strong> button and
   select <strong>OAuth client ID</strong>.
</li>
<li>Select the application type <strong>Other</strong>, enter the name
   "Drive API Quickstart", and click the <strong>Create</strong> button.</li>
<li>Click <strong>OK</strong> to dismiss the resulting dialog.

</li>
<li>Click the <span>file_download</span> (Download JSON)
   button to the right of the client ID.</li>
<li>Move this file to your working directory (in our case, 'resources' folder) and rename it <code>client_secret.json</code>.

</li>
</ol>

----------------------
In the first execution, the program will open a browser page to get the required permissions to your created Application in Step 1, Grant those permissions and Google will tell you that you can close the page, the program will continue once those permissions granted.
