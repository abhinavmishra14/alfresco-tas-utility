package org.alfresco.utility;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import org.alfresco.dataprep.CMISUtil.DocumentType;
import org.alfresco.utility.exception.TestConfigurationException;
import org.alfresco.utility.exception.TestObjectNotDefinedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;

public class Utility
{
    static Logger LOG = LogFactory.getLogger();

    public static void checkObjectIsInitialized(Object model, String message) throws Exception
    {
        if (model == null)
            throw new TestObjectNotDefinedException(message);
    }

    @SuppressWarnings("unused")
    public static File getTestResourceFile(String fileName) throws Exception
    {
        LOG.info("Get resource file test/resource/{}", fileName);
        File resource = new File(Utility.class.getClassLoader().getResource(fileName).getFile());
        if (resource == null)
        {
            throw new TestConfigurationException(String.format("[test/resource/%s] file was not found.", fileName));
        }
        return resource;

    }

    public static File getResourceTestDataFile(String fileName) throws Exception
    {
        return getTestResourceFile("testdata/" + fileName);
    }

    /**
     * @param fileName
     * @return the content of filename found in test/resources/testdata/
     *         <filename>
     * @throws Exception
     */
    public static String getResourceTestDataContent(String fileName) throws Exception
    {
        StringBuilder result = new StringBuilder("");
        File file = getResourceTestDataFile(fileName);

        try (Scanner scanner = new Scanner(file))
        {
            while (scanner.hasNextLine())
            {
                String line = scanner.nextLine();
                result.append(line).append("\n");
            }
            scanner.close();
        }
        catch (IOException e)
        {
            throw new TestConfigurationException(String.format("Cannot read from file %s. Error thrown: %s", fileName, e.getMessage()));
        }
        return result.toString();
    }

    public static String convertBackslashToSlash(String value)
    {
        if (SystemUtils.IS_OS_WINDOWS)
        {
            value = value.replace("\\", "/");
        }

        return value;
    }

    public static String cmisDocTypeToExtentions(DocumentType cmisDocumentType)
    {
        switch (cmisDocumentType)
        {
            case TEXT_PLAIN:
                return "txt";
            case HTML:
                return "html";
            case MSEXCEL:
                return "xls";
            case MSPOWERPOINT:
                return "ppt";
            case MSWORD:
                return "doc";
            case PDF:
                return "pdf";
            case XML:
                return "xml";
            default:
                break;
        }
        return "txt";
    }

    /**
     * Helper for building strings of the resource passed as parameter
     * 
     * @param parent
     * @param paths
     * @return concatenated paths of <parent> + each <paths>
     */
    public static String buildPath(String parent, String... paths)
    {
        StringBuilder concatenatedPaths = new StringBuilder(parent);
        int lenPaths = paths.length;
        if (lenPaths == 0)
            return concatenatedPaths.toString();

        if (!parent.endsWith("/"))
            concatenatedPaths.append("/");

        for (String path : paths)
        {
            if (!path.isEmpty())
            {
                concatenatedPaths.append(path);
                concatenatedPaths.append("/");
            }
        }
        String concatenated = concatenatedPaths.toString();
        if (lenPaths > 0 && paths[lenPaths - 1].contains("."))
            concatenated = StringUtils.removeEnd(concatenated, "/");
        return concatenated;
    }

    /**
     * If we have
     *"/test/something/now"
     * this method will return "/test/something"
     * @note the split char is set to "/" 
     * 
     * @param fullPath
     */
    public static String getParentPath(String fullPath)
    {
        String[] path = fullPath.split("/");
        String fileName = path[path.length - 1];
        return fullPath.replace(fileName, "");
    }

    /**
     * If the path ends with /, methods return the path without last /
     * 
     * @param sourcePath
     * @return sourcePath without last slash
     */
    public static String removeLastSlash(String sourcePath)
    {
        if (StringUtils.endsWith(sourcePath, "/"))
        {
            return StringUtils.removeEnd(sourcePath, "/");
        }
        return sourcePath;
    }
}