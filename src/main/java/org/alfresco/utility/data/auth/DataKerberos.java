package org.alfresco.utility.data.auth;

import org.alfresco.utility.TasProperties;
import org.alfresco.utility.exception.TestStepException;
import org.alfresco.utility.model.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.testng.Assert;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Context;
import javax.naming.directory.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.alfresco.utility.report.log.Step.STEP;

@Service
@Scope(value = "prototype")
public class DataKerberos
{
    @Autowired
    private TasProperties tasProperties;

    public enum UserAccountStatus
    {
        NORMAL_ACCOUNT(0x0200),
        PASSWD_NOTREQD(0x0020),
        DONT_REQ_PREAUTH(0x400000),
        TRUSTED_TO_AUTH_FOR_DELEGATION(0x1000000),
        TRUSTED_FOR_DELEGATION(0x80000),
        DONT_EXPIRE_PASSWD(0x10000);

        private final int value;

        private UserAccountStatus(int value)
        {
            this.value = value;
        }

        public int getValue()
        {
            return value;
        }
    }

    public Builder perform() throws NamingException
    {
        return new Builder();
    }

    private final static String USER_SEARCH_BASE = "CN=%s,CN=Users,DC=alfness,DC=com";

    private DirContext context;

    public class Builder implements UserManageable
    {

        public Builder() throws NamingException
        {
            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, tasProperties.getAuthContextFactory());
            properties.put(Context.PROVIDER_URL, tasProperties.getLdapURL());
            properties.put(Context.SECURITY_AUTHENTICATION, tasProperties.getSecurityAuth());
            properties.put(Context.SECURITY_PRINCIPAL, tasProperties.getLdapSecurityPrincipal());
            properties.put(Context.SECURITY_CREDENTIALS, tasProperties.getLdapSecurityCredentials());
            context = new InitialDirContext(properties);
        }

        @Override
        public Builder createUser(UserModel user) throws NamingException
        {
            STEP(String.format("[OracleDirServer] Add user %s", user.getUsername()));
            Attributes attributes = new BasicAttributes();
            Attribute objectClass = new BasicAttribute("objectClass");
            Attribute sn = new BasicAttribute("sn");
            Attribute samAccountName = new BasicAttribute("samAccountName");
            Attribute userPassword = new BasicAttribute("userPassword");
            Attribute userAccountControl = new BasicAttribute("userAccountControl");

            objectClass.add(DataLDAP.ObjectType.user.toString());
            sn.add(user.getLastName());
            samAccountName.add(user.getUsername());
            userPassword.add(user.getPassword());
            userAccountControl.add(Integer.toString(UserAccountStatus.NORMAL_ACCOUNT.getValue() + DataLDAP.UserAccountStatus.PASSWD_NOTREQD.getValue()
                    + UserAccountStatus.DONT_EXPIRE_PASSWD.getValue() + UserAccountStatus.TRUSTED_TO_AUTH_FOR_DELEGATION.getValue()
                    + UserAccountStatus.DONT_REQ_PREAUTH.getValue()));
            attributes.put(objectClass);
            attributes.put(sn);
            attributes.put(samAccountName);
            attributes.put(userPassword);
            attributes.put(userAccountControl);

            context.createSubcontext(String.format(USER_SEARCH_BASE, user.getUsername()), attributes);
            return this;
        }

        public SearchResult searchForObjectClass(String name, DataLDAP.ObjectType typeOfClass) throws NamingException
        {
            NamingEnumeration<SearchResult> results = null;
            String searchFilter = String.format("(objectClass=%s)", typeOfClass.toString());
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            try
            {
                results = context.search(String.format(USER_SEARCH_BASE, name), searchFilter, searchControls);
            }
            catch (NameNotFoundException e)
            {
                return null;
            }
            if (results.hasMoreElements())
                return (SearchResult) results.nextElement();
            return null;
        }

        @Override
        public UserManageable deleteUser(UserModel user) throws NamingException
        {
            STEP(String.format("[OracleDirServer] Delete user %s", user.getUsername()));
            context.destroySubcontext(String.format(USER_SEARCH_BASE, user.getUsername()));
            return this;
        }

        @Override
        public UserManageable updateUser(UserModel user, HashMap<String, String> attributes) throws NamingException
        {
            {
                STEP(String.format("[OracleDirServer] Update user %s", user.getUsername()));
                ModificationItem[] items = new ModificationItem[attributes.size()];
                int i = 0;
                for (Map.Entry<String, String> entry : attributes.entrySet())
                {
                    Attribute attribute = new BasicAttribute(entry.getKey(), entry.getValue());
                    items[i] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attribute);
                    i++;
                }
                context.modifyAttributes(String.format(USER_SEARCH_BASE, user.getUsername()), items);
                return this;
            }
        }

        @Override
        public UserManageable assertUserExists(UserModel user) throws NamingException
        {
            STEP(String.format("[OracleDirServer] Assert user %s exists", user.getUsername()));
            Assert.assertNotNull(searchForObjectClass(user.getUsername(), DataLDAP.ObjectType.user));
            return this;
        }

        @Override
        public UserManageable assertUserDoesNotExist(UserModel user) throws NamingException, TestStepException
        {
            STEP(String.format("[OracleDirServer] Assert user %s does not exist", user.getUsername()));
            Assert.assertNull(searchForObjectClass(user.getUsername(), DataLDAP.ObjectType.user));
            return this;
        }
    }
}
