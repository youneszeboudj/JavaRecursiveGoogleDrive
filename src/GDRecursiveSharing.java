import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.*;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Semaphore;

public class GDRecursiveSharing {
    /** Application name. */
    private static final String APPLICATION_NAME = "Recursive Google Drive sharing";

    /** Directory to store user credentials for this application. */
    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".credentials/drive-java-recursion");

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/drive-java-recursion
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.all().toArray(new String[]{}));
    private static Permission userPermission;
    private static Drive service;
    private static BatchRequest batch;
    private static JsonBatchCallback<Permission> callback;
    private static BufferedWriter logWriter;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static String currentTime(){
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        return(dateFormat.format(date));
    }

    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException in case the client_secret.json file is not found
     */
    private static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream("resources/client_secret.json");
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()).authorize("user");
        System.out.println(
                "Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Drive client service.
     * @return an authorized Drive client service
     * @throws IOException in case the authorization goes wrong
     */
    private static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Build a new authorized API client service.
        service = getDriveService();

        //callback to be triggered in case of sharing error/success
        callback = new JsonBatchCallback<Permission>() {
            @Override
            public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
                // Handle error
                System.err.println("ERR : "+e.getMessage()+" TIME : "+ currentTime());
                semaphore.release(); //decrement the semaphore, once it reaches 0 we start sharing the next folder
                okFolder= false; //there is an error so we have to re share all the folder cause a file may be missed
                logWriter.write("ERR : "+e.getMessage()+" TIME : "+ currentTime());
            }

            @Override
            public void onSuccess(Permission permission, HttpHeaders responseHeaders) throws IOException {
                semaphore.release();
                logWriter.write("Permission ID: " + permission.getId()+", NAME : "+permission.getDisplayName()+", TIME : "+ currentTime());
            }
        };

        //reading email
        Scanner scanner= new Scanner(System.in);
        System.out.println("Enter email address :");
        String email = scanner.nextLine();
        if (email.length()==0) {
            System.out.println("please enter something, otherwise the program will quit.");
            email = scanner.next();
            if (email.length()==0) return;
        }

        System.out.println("Please enter the id of the folder, you can find it when browsing to it on your browser, it looks like this : 0B97P21sguWTKMHVJYWlLMDFaMTg :");
        String rootID = scanner.next();
        if (rootID.length()==0) {
            System.out.println("please enter rootID, otherwise the program will quit.");
            rootID = scanner.next();
            if (rootID.length()==0) return;
        }

        //include trash
        System.out.println("Do you want to include files in trash? :");
        String includeTrash = scanner.next();
        if (includeTrash.length()==0) {
            System.out.println("please enter something (y or n), otherwise the program will quit.");
            includeTrash = scanner.next();
            if (includeTrash.length()==0) return;
        }
        char includeTrashedChar = includeTrash.toLowerCase().charAt(0);
        boolean includeTrashed= false;
        if(includeTrashedChar=='y')  includeTrashed= true;
        else if(includeTrashedChar!='n') return;

        //task type
        System.out.println("Do you want to revoke (r) or grant (g) permissions ? :");
        String next = scanner.next();
        if (next.length()==0) {
            System.out.println("please enter something (r or g), otherwise the program will quit.");
            next = scanner.next();
            if (next.length()==0) return;
        }
        final char taskType = next.toLowerCase().charAt(0);
        if(taskType=='r') {
            revokePermissions(email.toLowerCase(), includeTrashed, rootID);
            return;
        }else if(taskType!='g') return;

        //is notif on
        System.out.println("Do you want to send notifications for EACH shared file? (y)es or (n)o :");
        String sendNotifications = scanner.next();
        if (sendNotifications.length()==0) {
            System.out.println("please enter something (y or n), otherwise the program will quit.");
            sendNotifications = scanner.next();
            if (sendNotifications.length()==0) return;
        }
        char isNotif = sendNotifications.toLowerCase().charAt(0);
        boolean sendNotif= false;
        if(isNotif=='y')  sendNotif= true;
        else if(isNotif!='n') return;

        userPermission = new Permission()
                .setType("user")
                .setRole("reader")
                .setEmailAddress(email).set("sendNotificationEmails",sendNotif);

        Drive.Files.List request = service.files().list().setQ("'"+rootID+"' in parents and trashed="+includeTrashed);

        ArrayList<String> foldersToShare= new ArrayList<>();
        idNames= new HashMap<>(); //map each file's id to its name

        /* ROOT FOLDER */
        /* files on the root are shared here, folders are shared recursivly in the handleFolder method */
        do {
            try {
                FileList files = request.execute();

                final List<File> files1 = files.getFiles();
                for (File file : files1) {
                    if(file.getMimeType().compareTo("application/vnd.google-apps.folder")==0){
                        System.out.println("Sharing folder : "+file.getName());
                        foldersToShare.add(file.getId());
                        idNames.put(file.getId(), file.getName());
                    }else{
                        final boolean[] ok = {false};
                        do {
                            BatchRequest tmpBatch= service.batch();
                            Semaphore sem= new Semaphore(0);
                            service.permissions().create(file.getId(), userPermission).setSendNotificationEmail(false).queue(tmpBatch, new JsonBatchCallback<Permission>() {
                                @Override
                                public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
                                    ok[0] = false;
                                    System.out.println("Fail to share file, retrying , err :"+e.getMessage());
                                    sem.release();
                                }

                                @Override
                                public void onSuccess(Permission permission, HttpHeaders responseHeaders) throws IOException {
                                    //System.out.println("Permission ID: " + permission.getId());
                                    ok[0] = true;
                                    sem.release();
                                }
                            });
                            tmpBatch.execute();
                            sem.acquire();
                        }while(!ok[0]);

                        System.out.println("Shared FILE: " + file.getName());
                    }
                }

                request.setPageToken(files.getNextPageToken());
            } catch (IOException e) {
                System.out.println("ERR : " + e);
                request.setPageToken(null);
            } catch (InterruptedException e) {
                System.err.println("ERR : "+e.getMessage());
                request.setPageToken(null);
            }
        } while (request.getPageToken() != null && request.getPageToken().length() > 0);


        BufferedWriter bufferedWriter= new BufferedWriter(new FileWriter(new java.io.File("STATS.txt")));
        for (String folder : foldersToShare)
        {
            do{
                okFolder= true; folderSharingDone = false;
                try{
                    handleFolder(folder, includeTrashed);
                }catch(Exception e){
                    System.err.println("ERR : "+e.getMessage());
                }finally {
                    logWriter.close();
                }
            }while(!okFolder || !folderSharingDone);

             bufferedWriter.write("ONE TASK DONE : "+idNames.get(folder)+" - "+folder+" - "+all+" / "+folders+" / "+files+"\n");
        }
        bufferedWriter.close();
    }

    private static void revokePermissions(String email, boolean includeTrashed, String rootID) throws IOException, InterruptedException {
        Drive.Files.List request = service.files().list().setQ("'"+rootID+"' in parents and trashed="+includeTrashed+" and ('"+email+"' in owners or '"+email+"' in writers or '"+email+"' in readers)").setFields("nextPageToken, files(id, name, permissions)");

        batch = service.batch((HttpRequest request1) -> request1.setUnsuccessfulResponseHandler((request11, response, supportsRetry) -> false));

        do {
            try {
                FileList files = request.execute();

                List<File> files1 = files.getFiles();
                for (File file : files1) {
                    final List<Permission> permissions = file.getPermissions();

                    for (Permission permission : permissions) {
                        if(permission.getEmailAddress().toLowerCase().compareTo(email)==0){
                            service.permissions().delete(file.getId(), permission.getId()).queue(batch, new JsonBatchCallback<Void>() {
                                @Override
                                public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
                                    System.out.println(e.getMessage());
                                }

                                @Override
                                public void onSuccess(Void aVoid, HttpHeaders responseHeaders) throws IOException {
                                    System.out.println("SUCCESS");
                                }
                            });

                            if(batch.size()==50){
                                batch.execute();
                                Thread.sleep(5);
                            }
                        }
                    }
                }
                request.setPageToken(files.getNextPageToken());
            } catch (IOException e) {
                okFolder= false;
                System.out.println("ERR : " + e);
                request.setPageToken(null);
            }

        } while (request.getPageToken() != null && request.getPageToken().length() > 0);

        if(batch.size()>0){
            batch.execute();
        }

        System.out.println("TASK DONE.");
    }

    private static HashMap<String , String> idNames;
    private static boolean okFolder= true;
    private static boolean folderSharingDone = false;

    private static void handleFolder(String id, boolean includeTrashed) throws InterruptedException, IOException {
        all=0;
        folders=0;
        files=0;
        logWriter = new BufferedWriter(new FileWriter(new java.io.File(idNames.get(id).replace("/"," ")+".txt")));

        System.out.println("******************************** PROCESS BEGAN FOR : "+idNames.get(id));

        Drive.Files.List request = service.files().list().setQ("'"+id+"' in parents and trashed="+includeTrashed);

        batch = service.batch((HttpRequest request1) -> request1.setUnsuccessfulResponseHandler((request11, response, supportsRetry) -> false));

        do {
            try {
                FileList files = request.execute();

                final List<File> files1 = files.getFiles();
                for (File file : files1) {
                    System.out.println("Recursive sharing, folder : "+file.getName());
                    shareItem(file, includeTrashed);
                }

                request.setPageToken(files.getNextPageToken());
            } catch (IOException e) {
                okFolder= false;
                System.out.println("ERR : " + e);
                request.setPageToken(null);
            } catch (InterruptedException e) {
                okFolder= false;
                System.err.println("ERR : "+e.getMessage());
            }
        } while (request.getPageToken() != null && request.getPageToken().length() > 0);


        if(batch.size() >0)
        {
            commitBatch(batch);
        }
        System.out.println("all : "+all);
        System.out.println("folders : "+folders);
        System.out.println("files : "+files);
        folderSharingDone = true;
    }

    private static int all=0;
    private static  int folders=0, files=0;

    private static void shareItem(File file, boolean includeTrashed) throws IOException, InterruptedException {
        all++;
        final String mimeType = file.getMimeType();

        if(batch.size()>=5)
        {
            commitBatch(batch);
            batch= service.batch(request -> request.setUnsuccessfulResponseHandler((request1, response, supportsRetry) -> false));
        }

        if(mimeType.compareTo("application/vnd.google-apps.folder")==0){
            folders++;
            Drive.Files.List request = service.files().list().setQ("'"+file.getId()+"' in parents and trashed="+includeTrashed);

            do {
                try {
                    FileList files = request.execute();

                    final List<File> files1 = files.getFiles();
                    for (File f : files1)
                        shareItem(f, includeTrashed);
                    request.setPageToken(files.getNextPageToken());
                } catch (IOException e) {
                    System.out.println("ERR : " + e);
                    request.setPageToken(null);
                    okFolder= false;
                }
            } while (request.getPageToken() != null && request.getPageToken().length() > 0);
        }else
            files++;

        service.permissions().create(file.getId(), userPermission).setSendNotificationEmail(false).queue(batch, callback);
    }

    private static Semaphore semaphore;

    private static void commitBatch(BatchRequest batch) throws InterruptedException {
        Thread.sleep(1100);
        System.out.println("committing batch request");
        try {
            semaphore= new Semaphore(batch.size()*-1+1);
            batch.execute();
            semaphore.acquire();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            okFolder= false;
        }

        System.out.println("batch committed.");
    }

}