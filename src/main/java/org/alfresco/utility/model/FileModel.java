package org.alfresco.utility.model;

import java.io.File;

import org.alfresco.utility.Utility;
import org.alfresco.utility.data.RandomData;
import org.testng.reporters.Files;

public class FileModel extends ContentModel
{
    private String content="";
    private FileType fileType;
    
    public FileModel()
    {
        
    }

    public FileModel(String name)
    {
        super(name);
        setFileType(FileType.fromName(name));
    }

    public FileModel(String name, FileType fileType)
    {
        super(name);
        setFileType(fileType);
    }

    public FileModel(String name, String title, String description, FileType fileType)
    {
        super(name, title, description);
        setFileType(fileType);
    }

    public FileModel(String name, FileType fileType, String content)
    {
        this(name, fileType);
        setContent(content);
    }

    public FileModel(String name, String title, String description, FileType fileType, String content)
    {
        this(name, title, description, fileType);
        setContent(content);
    }
    
    public FileModel(FileModel originalFile)
    {
        this(originalFile.getName(), originalFile.getTitle(), originalFile.getDescription(), originalFile.getFileType(), originalFile.getContent());
        setCmisLocation(originalFile.getCmisLocation());
        setProtocolLocation(originalFile.getProtocolLocation());
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public FileType getFileType()
    {
        if(this.fileType==null)
            fileType = FileType.TEXT_PLAIN;
        
        return fileType;
    }

    public void setFileType(FileType fileType)
    {        
        this.fileType = fileType;
    }

    /**
     * Generates a new random {@link FileModel} object
     */
    public static FileModel getRandomFileModel(FileType fileType)
    {
        FileModel newFileModel = new FileModel(RandomData.getRandomFile(fileType));
        LOG.info("Generating new FileModel: {}", newFileModel.toString());
        return newFileModel;
    }
    
    /**
     * Generates a new random {@link FileModel} object with content
     */
    public static FileModel getRandomFileModel(FileType fileType, String content)
    {
        FileModel newFileModel = new FileModel(RandomData.getRandomFile(fileType));
        newFileModel.setContent(content);
        LOG.info("Generating new FileModel: {}", newFileModel.toString());
        return newFileModel;
    }
    
    public File toFile()
    {
        return new File(getName());
    }
    
    /**
     * This will generate a new FileModel having one existing file from src/main/resources/shared-data location
     * @param resourceDataFile
     * @return
     * @throws Exception
     */
    public static FileModel getFileModelBasedOnTestDataFile(String resourceDataFile) throws Exception
    {
        FileModel testFile = new FileModel(Utility.getResourceTestDataFile(resourceDataFile).toString());
        String content = Files.readFile(testFile.toFile()).replace("\n", "");
        testFile.setContent(content);

        return testFile;
    }
    
    /**
     * Clone file model to working copy. (e.g. 'test.txt' to 'test (Working Copy).txt' )
     * @return {@link FileModel}
     */
    public FileModel cloneAsWorkingCopy()
    {
        FileModel workingCopy = new FileModel();
        String type = FileType.fromName(getName()).extention;
        String nameNoType = getName().replace("." + type, "");
        String workingCopyName = nameNoType + " (Working Copy)." + type;
        workingCopy.setName(workingCopyName);
        workingCopy.setFileType(FileType.fromName(getName()));
        workingCopy.setContent(getContent());
        workingCopy.setCmisLocation(getCmisLocation().replace(getName(), workingCopyName));
        workingCopy.setProtocolLocation(getProtocolLocation().replace(getName(), workingCopyName));
        return workingCopy;
    }

    /**
     * Create a FileModel with content size of {@param size} MB
     *
     * e.g.: getFileModelWithContentSizeOfxMB(1) will return a FileModel with a content of 1 MB
     */
    public static FileModel getFileModelWithContentSizeOfxMB(int size) throws Exception
    {
        FileModel contentModel = new FileModel(RandomData.getRandomName("file"));
        contentModel.setContent(new String(new char[1024 * 1024 * size]));
        return contentModel;
    }
}
