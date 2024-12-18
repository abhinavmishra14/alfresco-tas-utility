package org.alfresco.utility.data;

import static org.alfresco.utility.report.log.Step.STEP;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.dataprep.CMISUtil.DocumentType;
import org.alfresco.dataprep.ContentActions;
import org.alfresco.dataprep.ContentAspects;
import org.alfresco.dataprep.ContentService;
import org.alfresco.dataprep.SiteService;
import org.alfresco.dataprep.UserService;
import org.alfresco.dataprep.AlfrescoHttpClient;
import org.alfresco.dataprep.AlfrescoHttpClientFactory;
import org.alfresco.utility.Utility;
import org.alfresco.utility.data.provider.XMLAspectData;
import org.alfresco.utility.exception.DataPreparationException;
import org.alfresco.utility.exception.IORuntimeException;
import org.alfresco.utility.exception.TestConfigurationException;
import org.alfresco.utility.model.ContentModel;
import org.alfresco.utility.model.FileModel;
import org.alfresco.utility.model.FileType;
import org.alfresco.utility.model.FolderModel;
import org.alfresco.utility.model.SiteModel;
import org.alfresco.utility.model.TagModel;
import org.alfresco.utility.model.UserModel;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStorageException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.testng.Assert;

/**
 * Utility for creating files and folders, asserting they exist in repository
 */
@Service
@Scope(value = "prototype")
public class DataContent extends TestData<DataContent>
{
    private Session session;

    private static Log logger = LogFactory.getLog(DataContent.class);

    @Autowired
    private AlfrescoHttpClientFactory alfrescoHttpClientFactory;

    @Autowired
    private ContentService contentService;

    @Autowired
    private ContentAspects contentAspect;

    @Autowired
    private ContentActions contentActions;

    @Autowired
    private SiteService siteService;

    @Autowired
    private UserService userService;

    private FileModel customModel;

    public ContentActions getContentActions()
    {
        return contentActions;
    }

    private Session getSession()
    {
        if (session == null)
        {
            return usingAdmin().session;
        }
        return session;
    }

    @Override
    public DataContent usingUser(UserModel user)
    {
        currentUser = user;
        session = contentService.getCMISSession(user.getUsername(), user.getPassword());
        return this;
    }

    @Override
    public DataContent usingAdmin()
    {
        return usingUser(getAdminUser());
    }

    /**
     * It will create a new folder in current resource
     * <code>
     * FolderModel newRandomFolder = FolderModel.getRandomFolderModel();
     * dataContent.usingUser(testUser).usingSite(testSite).createFolder(newRandomFolder);
     * <code>
     */
    public FolderModel createFolder(){
        return createFolder(new FolderModel(RandomData.getRandomName("Folder")));
    }

    /**
     * This is the entry point of the createFolder() method to make REST API or CMIS call.
     *
     * If the Alfresco version is at least 5.2 then the v1 REST API will be used.
     */
    public FolderModel createFolder(FolderModel folderModel)
    {
        AlfrescoHttpClient client = alfrescoHttpClientFactory.getObject();
        return createFolder(folderModel, (client.getAlfVersion() >= 5.2));
    }

    /**
     * Create a folder.
     *
     * @param folderModel The folder to create.
     * @param useV1API If true then the v1 REST API will be used, otherwise CMIS will be used.
     * @return The folder.
     */
    public FolderModel createFolder(FolderModel folderModel, boolean useV1API)
    {
        AlfrescoHttpClient client = alfrescoHttpClientFactory.getObject();
        if (useV1API)
        {
            return createFolderV1Api(client, folderModel, getCurrentUser().getUsername(), getCurrentUser().getPassword());
        }
        else
        {
            return createFolderCmisApi(folderModel.getName());
        }
    }

    /**
     * It will create a random folder in current resource using REST API
     * <code>
     * dataContent.usingUser(testUser).usingSite(testSite).createFolder();
     * <code>
     */
    public FolderModel createFolderV1Api(AlfrescoHttpClient client, FolderModel folderModel, String username, String password)
    {
        STEP(String.format("DATAPREP: Create folder '%s' in %s", folderModel.getName(), getCurrentSpace()));

        // Build request
        String nodeId = this.getLastNodeId();
        String reqUrl = client.getApiVersionUrl() + "nodes/" + nodeId + "/children";
        HttpPost post  = new HttpPost(reqUrl);
        JSONObject body = new JSONObject();
        body.put("name", folderModel.getName());
        body.put("nodeType", "cm:folder");

        JSONObject properties = new JSONObject();
        if (folderModel.getTitle() != null)
        {
            properties.put("cm:title", folderModel.getTitle());
        }
        if (folderModel.getDescription() != null)
        {
            properties.put("cm:description", folderModel.getDescription());
        }
        body.put("properties", properties);

        post.setEntity(client.setMessageBody(body));

        // Send Request
        logger.info(String.format("POST: '%s'", reqUrl));
        HttpResponse response = client.execute(username, password, post);
        if(HttpStatus.SC_CREATED == response.getStatusLine().getStatusCode())
        {
            JSONObject entryResponse = client.readStream(response.getEntity());
            JSONObject entryValueMap = (JSONObject) entryResponse.get("entry");

            folderModel.setNodeRef(entryValueMap.get("id").toString());
            folderModel.setName(entryValueMap.get("name").toString());

            String folderLocation = Utility.buildPath(getLastResource(), folderModel.getName());
            folderModel.setCmisLocation(folderLocation);
            folderModel.setProtocolLocation(folderLocation);

            logger.info(String.format("Successful created folder with id '%s' ", entryValueMap.get("id").toString()));
            return folderModel;
        }
        else
        {
            throw new RuntimeException("Could not create folder. Request response: " + client.getParameterFromJSON(response,"briefSummary", "error"));
        }
    }

    /**
     * It will create a random folder in current resource using CMIS
     * <code>
     * dataContent.usingUser(testUser).usingSite(testSite).createFolder();
     * <code>
     */
    public FolderModel createFolderCmisApi(String folderName)
    {
        STEP(String.format("DATAPREP: Create folder '%s' in %s", folderName, getCurrentSpace()));
        FolderModel folderModel = new FolderModel(folderName);
        String location = Utility.buildPath(getCurrentSpace(), folderName);
        setLastResource(location);
        Folder cmisFolder = contentService.createFolderInRepository(getSession(), folderName, getCurrentSpace());
        folderModel.setProtocolLocation(cmisFolder.getPath());
        folderModel.setCmisLocation(cmisFolder.getPath());
        folderModel.setNodeRef(cmisFolder.getId());
        return folderModel;
    }

    /**
     * Use this to delete the last resource, either file or folder
     * <code>
     *  dataContent.usingUser(adminUser).usingSite(siteModel).usingResource(repoFile).deleteContent();
     * </code>
     */
    public void deleteContent()
    {
        AlfrescoHttpClient client = alfrescoHttpClientFactory.getObject();

        if(client.getAlfVersion() >= 5.2)
        {
            deleteContentV1RestApi(client);
        }
        else
        {
            deleteContentCmisApi();
        }
    }

    /**
     * Use this to delete the last resource, either file or folder using V1 RestApi
     * <code>
     *  dataContent.usingUser(adminUser).usingSite(siteModel).usingResource(repoFile).deleteContent();
     * </code>
     */
    public void deleteContentV1RestApi(AlfrescoHttpClient client)
    {
        STEP(String.format("DATAPREP: Deleting '%s' with id '%s'", getLastResource(), getLastNodeId()));

        // Build request
        String nodeId = this.getLastNodeId();
        String reqUrl = client.getApiVersionUrl() + "nodes/" + nodeId;
        HttpDelete delete  = new HttpDelete(reqUrl);

        // Send Request
        logger.info(String.format("DELETE: '%s'", reqUrl));
        HttpResponse response = client.execute(currentUser.getUsername(), currentUser.getPassword(), delete);
        if(HttpStatus.SC_NO_CONTENT == response.getStatusLine().getStatusCode())
        {
            logger.info(String.format("Successful deleted content with id '%s' ", nodeId));
        }
        else
        {
            throw new RuntimeException("Could not delete file. Request response: " + client.getParameterFromJSON(response,"briefSummary", "error"));
        }
    }

    /**
     * Use this to delete the last resource, either file or folder using CmisApi
     * <code>
     *  dataContent.usingUser(adminUser).usingSite(siteModel).usingResource(repoFile).deleteContent();
     * </code>
     */
    public void deleteContentCmisApi()
    {
        STEP(String.format("DATAPREP: Deleting '%s'", getLastResource()));
        contentService.deleteContentByPath(getSession(), getLastResource());
    }

    /**
     * Use this to rename a file or a folder
     * <code>
     *        dataContent.usingUser(adminUser).usingSite(siteModel).usingResource(repoFolder).renameContent(newRepoFolder);
     * </code>
     *
     * @param newContent
     */
    public void renameContent(ContentModel newContent)
    {
        File file = new File(getLastResource());
        STEP(String.format("DATAPREP: Rename content '%s' in %s", file.getName(), getCurrentSpace()));
        contentActions.renameContent(getSession(), getLastResource(), newContent.getName());
    }

    /**
     * Add Email Alias aspect (emailserver:aliasable)
     * <code>
     *  dataContent.usingSite(testSite).usingResource(testFolder).addEmailAlias("aliasTas");
     * </code>
     *
     * @param alias
     * @return
     */
    public String addEmailAlias(String alias)
    {
        String folderName = new File(getLastResource()).getName();
        Utility.checkObjectIsInitialized(folderName, "getLastResource()");
        STEP(String.format("DATAPREP: Add 'Email Alias' aspect to folder '%s'", folderName));
        contentAspect.addEmailAlias(getCurrentUser().getUsername(), getCurrentUser().getPassword(), getCurrentSite(), folderName, alias);
        return alias;
    }

    /**
     * It will create a new file in current resource
     * <code>
     * FileModel newRandomFile = FileModel.getRandomFileModel();
     * dataContent.usingUser(testUser).usingSite(testSite).createContent(newRandomFile);
     * <code>
     */
    public FileModel createContent(){
        return createContent(new FileModel(RandomData.getRandomName("File")));
    }

    /**
     * This is the entry point of the createContent() method to make REST API or CMIS call
     */
    public FileModel createContent(FileModel fileModel){
        AlfrescoHttpClient client = alfrescoHttpClientFactory.getObject();

        if(client.getAlfVersion() >= 5.2)
        {
            FileModel createFile = createContentV1Api(client, fileModel);
            updateContent(client, createFile);
            return createFile;
        }
        else
        {
            return createContentCmisApi(fileModel);
        }
    }

    /**
     * Creates a file using REST API
     * Return the {@link FileModel} object on success creation
     *
     * @param client
     * @param fileModel
     * @return
     */
    public FileModel createContentV1Api(AlfrescoHttpClient client, FileModel fileModel)
    {
        STEP(String.format("DATAPREP: Create file '%s' in %s", fileModel.getName(), getCurrentSpace()));

        // Build request
        String nodeId = this.getLastNodeId();
        String reqUrl = client.getApiVersionUrl() + "nodes/" + nodeId + "/children";
        HttpPost post  = new HttpPost(reqUrl);
        JSONObject body = new JSONObject();
        body.put("name", fileModel.getName());
        body.put("nodeType", "cm:content");

        // Set Title or Description if specified
        JSONObject properties = new JSONObject();
        if (fileModel.getTitle() != null)
        {
            properties.put("cm:title", fileModel.getTitle());
        }
        if (fileModel.getDescription() != null)
        {
            properties.put("cm:description", fileModel.getDescription());
        }
        body.put("properties", properties);

        post.setEntity(client.setMessageBody(body));

        // Send Request
        logger.info(String.format("POST: '%s'", reqUrl));
        HttpResponse response = client.execute(currentUser.getUsername(), currentUser.getPassword(), post);
        if(HttpStatus.SC_CREATED == response.getStatusLine().getStatusCode())
        {
            JSONObject entryResponse = client.readStream(response.getEntity());
            JSONObject entryValueMap = (JSONObject) entryResponse.get("entry");

            fileModel.setNodeRef(entryValueMap.get("id").toString());
            fileModel.setName(entryValueMap.get("name").toString());
            String fileLocation = Utility.buildPath(getLastResource(), fileModel.getName());
            fileModel.setCmisLocation(fileLocation);
            fileModel.setProtocolLocation(fileLocation);
            if(fileModel.getContent().isEmpty())
            {
                fileModel.setContent("This is a test file");
            }
            logger.info(String.format("Successful created content with id '%s' ", entryValueMap.get("id").toString()));
            return fileModel;
        }
        else
        {
            throw new RuntimeException("Could not create file. Request response: " + client.getParameterFromJSON(response,"briefSummary", "error"));
        }
    }

    /**
     * This method updates the content of the created document
     *
     * @param client
     * @param fileModel
     */
    public void updateContent(AlfrescoHttpClient client, FileModel fileModel)
    {
        DocumentType documentType = fileModel.getFileType().getDocumentType();
        updateContent(client, fileModel, documentType);
    }

    /**
     * Creates a random document based on {@link DocumentType} passed using CMIS
     * Return the {@link Document} object on success creation
     * <code>
     * dataContent.usingSite(site).createContent(sourceFile);
     * </code>
     *
     * @param fileModel
     * @return
     * @throws DataPreparationException
     */
    public FileModel createContentCmisApi(FileModel fileModel) throws DataPreparationException
    {
        String fileFullName = fileModel.getName();
        if (FilenameUtils.getExtension(fileFullName).length() == 0)
            fileFullName = String.format("%s.%s", fileModel.getName(), fileModel.getFileType().extension);

        STEP(String.format("DATAPREP: Creating a new non-empty content %s in %s ", fileModel.getName(), getLastResource()));
        if (getLastResource().isEmpty())
            setLastResource(RandomData.getRandomName("Folder"));

        Document cmisDocument = null;
        try
        {
            cmisDocument = contentService.createDocumentInRepository(getSession(), getLastResource(), DocumentType.valueOf(fileModel.getFileType().toString()),
                    fileFullName, fileModel.getContent());

            // Set Title or Description if specified
            if (fileModel.getTitle() != null || fileModel.getDescription() != null)
            {
                Map<String, Object> properties = new HashMap<String, Object>();
                {
                    properties.put("cm:title", fileModel.getTitle());
                    properties.put("cm:description", fileModel.getDescription());
                }

                cmisDocument.updateProperties(properties);
            }
        }
        catch (CmisStorageException cse)
        {
            cmisDocument = contentService.createDocumentInRepository(getSession(), getLastResource(), DocumentType.valueOf(fileModel.getFileType().toString()),
                    fileFullName, fileModel.getContent());
        }
        String fileLocation = Utility.buildPath(getLastResource(), fileFullName);
        fileModel.setCmisLocation(fileLocation);
        fileModel.setProtocolLocation(fileLocation);
        fileModel.setNodeRef(cmisDocument.getId());
        return fileModel;
    }

    /**
     * This is the entry point of the createContent() method to make REST API or CMIS call
     */
    public FileModel createContent(DocumentType documentType) throws DataPreparationException
    {
        AlfrescoHttpClient client = alfrescoHttpClientFactory.getObject();

        if(client.getAlfVersion() >= 5.2)
        {
            String fileName = RandomData.getRandomName("file");
            FileModel fileModel = new FileModel(fileName, FileType.fromName(fileName + "." + documentType.extention));
            FileModel createFile = createContentDocTypeV1Api(client, fileModel, documentType);
            updateContent(client, createFile, documentType);
            return createFile;
        }
        else
        {
            return createContentDocTypeCmisApi(documentType);
        }
    }

    /**
     * Creates a random document based on {@link DocumentType} passed using REST API
     * Return the {@link Document} object on success creation
     * <code>
     * dataContent.usingUser(userModel).usingResource(myFolder).createContent(DocumentType.TEXT_PLAIN);
     * </code>
     *
     * @param client
     * @param fileModel
     * @param documentType
     * @return
     * @throws DataPreparationException
     */
    public FileModel createContentDocTypeV1Api(AlfrescoHttpClient client, FileModel fileModel, DocumentType documentType)
    {
        fileModel.setName(String.format("%s.%s", fileModel.getName(), Utility.cmisDocTypeToExtentions(documentType)));
        return createContentV1Api(client, fileModel);
    }

    /**
     * This method updates the content of the created document
     *
     * @param client
     * @param fileModel
     * @param documentType
     */
    public void updateContent(AlfrescoHttpClient client, FileModel fileModel, DocumentType documentType)
    {
        // Build request
        String nodeId = fileModel.getNodeRef();
        String reqUrl = client.getApiVersionUrl() + "nodes/" + nodeId + "/content?majorVersion=true";

        HttpPut put  = new HttpPut(reqUrl);
        String fileContent = fileModel.getContent();
        if (documentType != null)
        {
            String contentType = documentType.type + ";charset=" + client.UTF_8_ENCODING;
            put.addHeader("Content-Type", contentType);
        }
        StringEntity se = new StringEntity(fileContent, client.UTF_8_ENCODING);
        se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, client.MIME_TYPE_JSON));
        put.setEntity(se);

        // Send Request
        logger.info(String.format("Update content for file with name '%s' by POST: '%s'", fileModel.getName(), reqUrl));
        HttpResponse response = client.executeAndRelease(currentUser.getUsername(), currentUser.getPassword(), put);
        if(HttpStatus.SC_OK == response.getStatusLine().getStatusCode())
        {
            logger.info(String.format("Successfully updated content"));
        }
        else
        {
            throw new RuntimeException("Could not update file. Request response: " + client.getParameterFromJSON(response,"briefSummary", "error"));
        }
    }

    /**
     * Creates a random document based on {@link DocumentType} passed using CMIS
     * Return the {@link Document} object on success creation
     * <code>
     * dataContent.usingUser(userModel).usingResource(myFolder).createContent(DocumentType.TEXT_PLAIN);
     * </code>
     *
     * @param documentType
     * @return
     * @throws DataPreparationException
     */
    public FileModel createContentDocTypeCmisApi(DocumentType documentType) throws DataPreparationException
    {
        String newContent = String.format("%s.%s", RandomData.getRandomName("file"), Utility.cmisDocTypeToExtentions(documentType));
        String newLocation = Utility.buildPath(getLastResource(), newContent);
        STEP(String.format("DATAPREP: Creating a new non-empty content %s in %s ", newContent, getLastResource()));

        if (getLastResource().isEmpty())
            setLastResource(RandomData.getRandomName("Folder"));

        Document cmisDocument = null;
        try
        {
            cmisDocument = contentService.createDocumentInRepository(getSession(), getLastResource(), documentType, newContent, "This is a file file");
        }
        catch (CmisStorageException cse)
        {
            cmisDocument = contentService.createDocumentInRepository(getSession(), getLastResource(), documentType, newContent, "This is a file file");
        }
        FileModel newFile = new FileModel(cmisDocument.getName());
        newFile.setCmisLocation(newLocation);
        newFile.setProtocolLocation(newLocation);
        newFile.setNodeRef(cmisDocument.getId());
        return newFile;
    }

    public void assertContentExist() throws TestConfigurationException
    {
        STEP(String.format("DATAPREP: Check that content %s exists.", getLastResource()));
        boolean contentExist = checkContent(getLastResource(), getCurrentUser());
        Assert.assertTrue(contentExist, String.format("Content {%s} was found in repository", getLastResource()));
    }

    public void assertContentDoesNotExist() throws TestConfigurationException
    {
        STEP(String.format("DATAPREP: Check that content does not %s exist.", getLastResource()));
        boolean contentDoesNotExist = checkContent(getLastResource(), getCurrentUser());
        Assert.assertFalse(contentDoesNotExist, String.format("Content {%s} was NOT found in repository", getLastResource()));
    }

    /**
     * @param fullPath - the full path to CMIS object
     * @param userModel
     * @throws TestConfigurationException
     */
    public boolean checkContent(String fullPath, UserModel userModel) throws TestConfigurationException
    {
        if (fullPath == null || fullPath.isEmpty())
        {
            throw new TestConfigurationException("You didn't specify your #lastResource. Please call #usingResource(..) or #setLastResource(...) methods");
        }
        return !contentService.getNodeRefByPath(getSession(), Utility.convertBackslashToSlash(fullPath)).isEmpty();
    }

    public void waitUntilContentIsDeleted(String fullPath)
    {
        int retry = 0;
        String deletedObject = contentService.getNodeRefByPath(getSession(), fullPath);
        while (!StringUtils.isEmpty(deletedObject) && retry < Utility.retryCountSeconds)
        {
            Utility.waitToLoopTime(1);
            deletedObject = contentService.getNodeRefByPath(getSession(), fullPath);
            retry++;
        }
    }

    /**
     * Wait and retry for 15 seconds checking if a resource is created
     *
     * @param fullPath
     */
    public void waitUntilContentIsCreated(String fullPath)
    {
        LOG.info("Wait until new content is created up to 15 seconds");
        int retry = 0;
        String newObject = contentService.getNodeRefByPath(getSession(), fullPath);
        while (StringUtils.isEmpty(newObject) && retry < 15)
        {
            Utility.waitToLoopTime(1);
            newObject = contentService.getNodeRefByPath(getSession(), fullPath);
            retry++;
        }
    }

    /**
     * Wait and retry for 15 seconds checking if a resource is created for Linux OS
     *
     * @param fullPath
     */
    public void waitUntilContentIsCreatedInLinux(String fullPath)
    {
        if (SystemUtils.IS_OS_LINUX)
        {
            waitUntilContentIsCreated(fullPath);
        }
    }

    /**
     * Delete a site
     *
     * @param site
     */
    @SuppressWarnings("deprecation")
    public void deleteSite(SiteModel site)
    {
        if (siteService.exists(site.getId(), getAdminUser().getUsername(), getAdminUser().getPassword()))
        {
            LOG.info("Deleting site {} with user {}", site.toString(), getCurrentUser().toString());
            siteService.delete(getCurrentUser().getUsername(), getCurrentUser().getPassword(), getCurrentUser().getDomain(), site.getId());
        }
    }

    /**
     * Delete parent folder along with all children in it
     *
     * @param from
     */
    public void deleteTree(FolderModel from)
    {
        LOG.info("Deleting entire tree of {}", from.getCmisLocation());
        contentService.deleteTreeByPath(getSession(), from.getCmisLocation());
    }

    /**
     * Deploy a custom content model to repository
     * http://docs.alfresco.com/5.1/tasks/deploy-dynamic.html
     *
     * @param localModelXMLFilePath
     * @throws TestConfigurationException
     */
    public void setCustomModel(String localModelXMLFilePath)
    {
        File file = Utility.getTestResourceFile(localModelXMLFilePath);

        LOG.info("Deploying custom content Model from XML file: {}", file.getPath());
        FileInputStream inputStream = null;
        try
        {
            inputStream = new FileInputStream(file);
        }
        catch (FileNotFoundException ef)
        {
            throw new TestConfigurationException("Could not find your custom model XML file provided:" + ef.getMessage());
        }

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(PropertyIds.OBJECT_TYPE_ID, "D:cm:dictionaryModel");
        props.put(PropertyIds.NAME, file.getName());
        props.put("cm:modelActive", true);
        Session session = contentService.getCMISSession(getCurrentUser().getUsername(), getCurrentUser().getPassword());
        ContentStream contentStream = session.getObjectFactory().createContentStream(file.getName(), file.length(), FilenameUtils.getExtension(file.getPath()),
                inputStream);
        CmisObject modelInRepo;
        // it will throw exception if object is not found, so in that case we will upload it
        try
        {
            modelInRepo = session.getObjectByPath(String.format("/Data Dictionary/Models/%s", file.getName()));
            if (modelInRepo != null)
            {
                LOG.info("Custom Content Model [{}] is already deployed under [/Data Dictionary/Models/] location", localModelXMLFilePath);
            }
        }
        catch (Exception e)
        {
            Folder model = (Folder) session.getObjectByPath("/Data Dictionary/Models");
            modelInRepo = model.createDocument(props, contentStream, VersioningState.MAJOR);
            LOG.info("Custom Content Model [{}] is now deployed under [/Data Dictionary/Models/] location", localModelXMLFilePath);
        }

        this.customModel = new FileModel(modelInRepo.getName());
        this.customModel.setNodeRef(modelInRepo.getId());
        this.customModel.setNodeRef( this.customModel.getNodeRefWithoutVersion());
        this.customModel.setCmisLocation(String.format("/Data Dictionary/Models/%s", file.getName()));
        LOG.info("Custom Model file: " + this.customModel.getCmisLocation());
    }

    public void deployContentModel(String localModelXMLFilePath) throws TestConfigurationException
    {
        setCustomModel(localModelXMLFilePath);
    }

    /**
     * Returns customModel file is one exists or is deployed using deployContentModel
     * otherwise null
     */
    public FileModel getCustomModel()
    {
        return customModel;
    }

    /**
     * Create a content stream from some content.
     *
     * @param fileName A name for the content stream.
     * @param content The content.
     * @return The stream.
     * @throws IORuntimeException if there is an issue creating the stream.
     */
    public ContentStream getContentStream(String fileName, String content)
    {
        if (content == null)
        {
            content = "";
        }
        ContentStream contentStream;
        try
        {
            byte[] byteContent = content.getBytes("UTF-8");
            InputStream stream = new ByteArrayInputStream(byteContent);
            DataInputStream dataInputStream = new DataInputStream(stream);
            byteContent = new byte[content.length()];
            dataInputStream.readFully(byteContent);
            ByteArrayInputStream bInput = new ByteArrayInputStream(byteContent);
            contentStream = new ContentStreamImpl(fileName, BigInteger.valueOf(byteContent.length), FileType.fromName(fileName).mimeType, bInput);
            bInput.close();
            dataInputStream.close();
            stream.close();
        }
        catch (IOException e)
        {
            throw new IORuntimeException(e);
        }
        return contentStream;
    }

    public void closeContentStream(ContentStream contentStream)
    {
        try
        {
            contentStream.getStream().close();
        }
        catch (IOException e)
        {
            LOG.error("Unable to close the content stream", e);
        }
    }

    /**
     * @param contentModel
     * @param objectTypeID
     *            Example: objectTypeID = "D:cmis:document"
     * @throws IORuntimeException if there is an issue creating the content.
     */
    public ContentModel createCustomContent(ContentModel contentModel, String objectTypeID, CustomObjectTypeProperties objectTypeProperty)
    {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(PropertyIds.OBJECT_TYPE_ID, objectTypeID);
        properties.put(PropertyIds.NAME, contentModel.getName());

        List<Object> aspects = new ArrayList<Object>();
        aspects.add("P:cm:titled");
        properties.put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS, aspects);
        properties.put("cm:title", contentModel.getTitle());
        properties.put("cm:description", contentModel.getDescription());
        File fullPath = new File(String.format("%s/%s", getCurrentSpace(), contentModel.getName()));
        String parentFolder = Utility.convertBackslashToSlash(fullPath.getParent());

        LOG.info("Creating custom Content Model {} in: {}", contentModel.toString(), fullPath.getPath());
        CmisObject parentCMISFolder = contentService.getCmisObject(getCurrentUser().getUsername(), getCurrentUser().getPassword(), parentFolder);
        if (parentCMISFolder instanceof Document)
            throw new TestConfigurationException(String.format("It seems the parent folder of your resource %s is a file", fullPath));

        Folder folder = (Folder) parentCMISFolder;
        if (contentModel instanceof FolderModel)
        {
            STEP(String.format("DATAPREP: Create custom Folder '%s' with typeID: %s, in '%s'", contentModel.getName(), objectTypeID, getCurrentSpace()));
            Folder newFolder = folder.createFolder(properties);
            if (objectTypeProperty != null)
            {
                objectTypeProperty.applyPropertiesTo(newFolder);
            }
            contentModel.setNodeRef(newFolder.getId());
        }
        if (contentModel instanceof FileModel)
        {
            FileModel fileModel = (FileModel) contentModel;
            STEP(String.format("DATAPREP: Create custom File '%s' with typeID: %s, in '%s'", contentModel.getName(), objectTypeID, getCurrentSpace()));
            ContentStream contentStream = getContentStream(contentModel.getName(), fileModel.getContent());
            Document newFile = folder.createDocument(properties, contentStream, VersioningState.MAJOR);

            if (objectTypeProperty != null)
            {
                objectTypeProperty.applyPropertiesTo(newFile);
            }
            contentModel.setNodeRef(newFile.getId());
            closeContentStream(contentStream);
        }
        contentModel.setProtocolLocation(fullPath.getPath());
        contentModel.setCmisLocation(fullPath.getPath());
        return contentModel;
    }

    /**
     * Creates a tag for a content file
     * You must use this in correlation with {@link DataContent#usingResource(ContentModel)}
     *
     * @param fileModel
     * @param model tag model
     * @throws TestConfigurationException
     */
    public void addTagToContent(TagModel model) throws TestConfigurationException
    {
        STEP(String.format("DATAPREP: Create '%s' tag ", model.getTag()));
        if (getLastResource() == null || getLastResource().isEmpty())
            throw new TestConfigurationException("You didn't specify your last resource in your tests. Please call usingResource(...) before adding a tag");

        contentActions.addSingleTag(getCurrentUser().getUsername(), getCurrentUser().getPassword(), getLastResource(), model.getTag());
    }

    /**
     * Verify content has tag
     *
     * @param cmisObjectPath
     * @param model
     */
    public void assertContentHasTag(String cmisObjectPath, TagModel model)
    {
        STEP(String.format("DATAPREP: Verify content %s has tag %s", cmisObjectPath, model.getTag()));
        List<String> tags = contentActions.getTagNamesFromContent(getCurrentUser().getUsername(), getCurrentUser().getPassword(), cmisObjectPath);
        boolean found = false;
        for (String tag : tags)
        {
            if (model != null && model.getTag() != null && tag.equals(model.getTag().toLowerCase()))
            {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found, "content has tag");
    }

    /**
     * @return nodeRef of the current resource used
     *         You can use this in correlation with {@link DataContent#usingResource(ContentModel)} and/or {@link DataContent#usingSite(SiteModel)}, etc.
     */
    public String getNodeRef()
    {
        return contentService.getNodeRefByPath(getSession(), Utility.convertBackslashToSlash(getLastResource()));
    }

    /**
     * Get the corresponding CMIS Document Object of a file using only the file path
     *
     * @param filePath
     * @return {@link Document}
     */
    public Document getCMISDocument(String filePath)
    {
        return contentService.getDocumentObject(getSession(), filePath);
    }

    /**
     * Get the corresponding CMIS Folder Object of a file using only the file path
     *
     * @param filePath
     * @return {@link Folder}
     */
    public Folder getCMISFolder(String folderPath)
    {
        return contentService.getFolderObject(getSession(), folderPath);
    }

    /**
     * Adding aspects to ContentModels
     *
     * @param object
     */
    public void addAspect(List<XMLAspectData> aspects)
    {
        List<String> allAspectNames = new ArrayList<String>();
        for (XMLAspectData aspect : aspects)
        {
            allAspectNames.add(aspect.getName());
        }
        contentAspect.addAspect(getSession(), getLastResource(), allAspectNames.toArray(new String[0]));

        // now add the properies corelated to each aspect
        for (XMLAspectData aspect : aspects)
        {
            if (aspect.hasProperties())
            {
                LOG.info("Preparing to apply {}", aspect.toString());
                contentActions.addProperties(getCurrentUser().getUsername(), getCurrentUser().getPassword(), getLastResource(),
                        aspect.getPropertiesAsHashMap());
            }
        }
    }

    /**
     * Add file to favorites
     *
     * @throws DataPreparationException
     */
    public void addFileToFavorites(FileModel file) throws DataPreparationException
    {
        STEP(String.format("DATAPREP: Add file %s.%s to Favorites", file.getName(), file.getFileType().extension));
        contentActions.setFileAsFavorite(getCurrentUser().getUsername(), getCurrentUser().getPassword(), getCurrentSite(),
                String.format("%s.%s", file.getName(), file.getFileType().extension));
    }

    /**
     * Add folder to favorites
     *
     * @throws DataPreparationException
     */
    public void addFolderToFavorites(FolderModel folder) throws DataPreparationException
    {
        STEP(String.format("DATAPREP: Add folder %s to Favorites", folder.getName()));
        contentActions.setFolderAsFavorite(getCurrentUser().getUsername(), getCurrentUser().getPassword(), getCurrentSite(), folder.getName());
    }

    /**
     * Asserting the version of the content
     *
     * @param version
     */
    public void assertContentVersionIs(String version)
    {
        STEP(String.format("DATAPREP: Asserting that the version of the '%s' content is %s", getLastResource(), version));
        String currentVersion = getCMISDocument(getLastResource()).getVersionLabel();
        Assert.assertEquals(currentVersion, version);
    }

    /**
     * Asserting the size of the content
     *
     * @param sizeInBytes size in bytes
     */
    public void assertContentSizeIs(long sizeInBytes)
    {
        STEP(String.format("DATAPREP: Asserting that the size of the '%s' content is %s", getLastResource(), sizeInBytes));
        Document lastVersionDocument = getCMISDocument(getLastResource());
        lastVersionDocument.refresh();
        Assert.assertEquals(lastVersionDocument.getContentStreamLength(), sizeInBytes, "The size of the content is not as expected.");
    }

    /**
     * Check out document
     */
    public void checkOutDocument()
    {
        STEP(String.format("DATAPREP: Check out document %s", getLastResource()));
        contentActions.checkOut(getSession(), getLastResource());
    }

    /**
     * Check out document
     */
    public void cancelCheckOut()
    {
        STEP(String.format("DATAPREP: Cancel check out on document %s", getLastResource()));
        contentActions.cancelCheckOut(getSession(), getLastResource());
    }

    /**
     * Update document content
     * @param newContent
     */
    public void updateContent(String newContent)
    {
        String path = getLastResource();
        STEP(String.format("DATAPREP: Update content for document from %s", path));
        contentService.updateDocumentContent(getCurrentUser().getUsername(), getCurrentUser().getPassword(), path, newContent);
    }

    /**
     * Check in document.
     *
     * @param newContent
     * @param majorVersion true to set major version(e.g. 2.0)
     * @param checkInComment
     */
    public void checkIn(String newContent, boolean majorVersion, String checkInComment)
    {
        String docName = new File(getLastResource()).getName();
        STEP(String.format("DATAPREP: Check in document %s", docName));
        contentActions.checkIn(getSession(), getLastResource(), newContent, majorVersion, checkInComment);
    }

    public void setInheritPermissions(boolean inheritPermissions)
    {
        String contentPath = getLastResource();
        STEP(String.format("DATAPREP: Set inherit permissions to %s for %s", inheritPermissions, contentPath));
        contentActions.setInheritPermissions(getCurrentUser().getUsername(),  getCurrentUser().getPassword(), contentPath, inheritPermissions);
    }
    public void emptyUserTrashcan(UserModel user)
    {
        userService.emptyTrashcan(user.getUsername(), user.getPassword());
    }

    public FileModel uploadDocument(File fileToUpload)
    {
        contentService.uploadFileInRepository(getCurrentUser().getUsername(), getCurrentUser().getPassword(),
               Utility.removeLastSlash(getLastResource()), fileToUpload.getAbsolutePath());
        FileModel alfrescoFile = new FileModel(fileToUpload.getName());
        alfrescoFile.setCmisLocation(Utility.buildPath(getLastResource(), fileToUpload.getName()));
        return alfrescoFile;
    }
}
