package org.alfresco.utility.dsl;

import static org.alfresco.utility.Utility.checkObjectIsInitialized;

import org.alfresco.utility.JmxClient;
import org.alfresco.utility.LogFactory;
import org.alfresco.utility.data.DataContent;
import org.alfresco.utility.data.LastTestData;
import org.alfresco.utility.exception.JmxException;
import org.alfresco.utility.exception.TestConfigurationException;
import org.alfresco.utility.model.SiteModel;
import org.alfresco.utility.model.UserModel;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;

/**
 * Wrapper on Client
 *
 * @param <Client>
 */
public abstract class DSLWrapper<Client> implements DSLEndPoint
{

    @Autowired
    DataContent dataContent;

    @Autowired
    protected JmxClient jmxClient;

    protected Logger LOG = LogFactory.getLogger();

    private String currentRepositorySpace = null;
    private LastTestData lastTestDataCreated = new LastTestData(this);

    // HELPERS ----------------------------------------------------------

    public String getRootPath() throws TestConfigurationException
    {
        return String.format("%s/%s", getRepositoryPrefixPath(), "/");
    }

    public String getSitesPath() throws TestConfigurationException
    {
        return String.format("%s/%s", getRepositoryPrefixPath(), "Sites");
    }

    public String getUserHomesPath() throws TestConfigurationException
    {
        return String.format("%s/%s", getRepositoryPrefixPath(), "User Homes");
    }

    public String getDataDictionaryPath() throws TestConfigurationException
    {
        return String.format("%s/%s", getRepositoryPrefixPath(), "Data Dictionary");
    }

    /**
     * @return true/false if current protocl is enabled on server
     * @throws Exception
     */
    public boolean isProtocolEnabled() throws Exception
    {
        if (!jmxClient.isJMXEnabled())
            throw new JmxException("JMX not enabled on server");

        LOG.info("Check [{}] protocol is enabled", getProtocolName());

        String status = getProtocolJMXConfigurationStatus();

        jmxClient.closeConnection();
        return status.equals("true");
    }

    /**
     * @param parent
     * @param paths
     * @return concatenated paths of <parent> + each <paths>
     */
    protected String buildPath(String parent, String... paths)
    {
        StringBuilder concatenatedPaths = new StringBuilder(parent);
        for (String path : paths)
        {
            concatenatedPaths.append(path);
            concatenatedPaths.append("/");
        }
        return concatenatedPaths.toString();
    }

    /**
     * @param siteId
     * @param filesOrFoldersHierarcy
     * @return the full path of <filesOrFoldersHierarcy> inside /Sites/documentLibrary
     * @throws TestConfigurationException
     */
    protected String buildSiteDocumentLibraryPath(String siteId, String... filesOrFoldersHierarcy) throws TestConfigurationException
    {
        String root = String.format("%s/%s/%s", getSitesPath(), siteId, "documentLibrary");
        return buildPath(root, filesOrFoldersHierarcy);
    }

    /**
     * @return the current Repository Space
     *         If nothing is specified, the root folder is used
     * @throws TestConfigurationException
     */
    @Override
    public String getCurrentRepositorySpace() throws TestConfigurationException
    {
        if (currentRepositorySpace == null)
            currentRepositorySpace = getRootPath();

        return currentRepositorySpace;
    }

    public void setCurrentRepositorySpace(String currentRepositorySpace)
    {
        this.currentRepositorySpace = currentRepositorySpace;
    }

    public String getLastTestDataCreated()
    {
        return lastTestDataCreated.getFullPath();
    }

    public void setLastTestDataCreated(String fullPath)
    {
        this.lastTestDataCreated.setFullPath(fullPath);
    }

    public String getProtocolName()
    {
        return this.getClass().getSimpleName().replaceAll("Wrapper", "");
    }

    // DSL ----------------------------------------------------------

    /**
     * Just verify using JMX calls if the protocl is enabled on server or not
     */
    public void assertProtocolIsEnabled() throws Exception
    {
        Assert.assertTrue(isProtocolEnabled(), String.format("%s protocol is enabled", getProtocolName()));
    }

    /**
     * get the current status true/false of the protocol on test server
     * 
     * @return
     * @throws Exception
     */
    protected abstract String getProtocolJMXConfigurationStatus() throws Exception;

    @SuppressWarnings("unchecked")
    public Client usingSite(String siteId) throws Exception
    {
        checkObjectIsInitialized(siteId, "SiteID");
        setCurrentRepositorySpace(buildSiteDocumentLibraryPath(siteId, ""));
        return (Client) this;
    }

    @SuppressWarnings("unchecked")
    public Client usingSite(SiteModel siteModel) throws Exception
    {
        checkObjectIsInitialized(siteModel, "SiteModel");
        String path = buildSiteDocumentLibraryPath(siteModel.getId(), "");
        setCurrentRepositorySpace(path);
        return (Client) this;
    }

    @SuppressWarnings("unchecked")
    public Client and()
    {
        return (Client) this;
    }
    
    @SuppressWarnings("unchecked")
    public Client then()
    {
        return (Client) this;
    }

    @SuppressWarnings("unchecked")
    public Client assertThatExistsInRepo()
    {
        dataContent.assertContentExist(getLastTestDataCreated());
        return (Client) this;
    }

    public abstract Client authenticateUser(UserModel userModel) throws Exception;

    public abstract Client disconnect() throws Exception;

}