package org.alfresco.utility.data.auth;

import org.alfresco.utility.TasProperties;
import org.alfresco.utility.exception.TestStepException;
import org.alfresco.utility.model.GroupModel;
import org.alfresco.utility.model.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.testng.Assert;

import javax.naming.*;
import javax.naming.directory.*;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.alfresco.utility.report.log.Step.STEP;

@Service
@Scope(value = "prototype")
public class DataLDAP
{
    enum ObjectType
    {
        user("user"), group("group");

        private final String objectType;

        ObjectType(String objectType)
        {
            this.objectType = objectType;
        }

        @Override
        public String toString()
        {
            return objectType;
        }
    }
    public enum UserAccountControlValue
    {
        enabled("512"), disabled("514"), enabledPasswordNotRequired("544"), disabledPasswordNotRequired("546");

        private final String userAccountControlValue;

        UserAccountControlValue(String userAccountControlValue)
        {
            this.userAccountControlValue = userAccountControlValue;
        }

        @Override
        public String toString()
        {
            return userAccountControlValue;
        }
    }

    enum UserAccountStatus
    {
        ACCOUNTDISABLE(0x0002), NORMAL_ACCOUNT(0x0200), PASSWD_NOTREQD(0x0020), PASSWORD_EXPIRED(0x800000), DONT_EXPIRE_PASSWD(0x10000);

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

    @Autowired
    private TasProperties tasProperties;

    private DirContext context;

    public DataLDAP.Builder perform() throws NamingException
    {
        return new DataLDAP.Builder();
    }

    public DataLDAP.Builder performLdap2() throws NamingException
    {
        return new DataLDAP.Builder(tasProperties.getLdap2URL(), tasProperties.getLdap2SecurityPrincipal(), tasProperties.getLdap2SecurityCredentials(), tasProperties.getLdapSearchBase2());
    }

    public class Builder implements UserManageable, GroupManageable
    {
        private String searchBase = "";
        public Builder() throws NamingException
        {
            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, tasProperties.getAuthContextFactory());
            properties.put(Context.PROVIDER_URL, tasProperties.getLdapURL());
            properties.put(Context.SECURITY_AUTHENTICATION, tasProperties.getSecurityAuth());
            properties.put(Context.SECURITY_PRINCIPAL, tasProperties.getLdapSecurityPrincipal());
            properties.put(Context.SECURITY_CREDENTIALS, tasProperties.getLdapSecurityCredentials());
            searchBase = tasProperties.getLdapSearchBase();
            context = new InitialDirContext(properties);
        }

        public Builder(String ldapURL, String ldapSecurityPrincipal, String ldapSecurityCredentials, String ldapSearchBase) throws NamingException
        {
            Properties properties = new Properties();
            properties.put(Context.INITIAL_CONTEXT_FACTORY, tasProperties.getAuthContextFactory());
            properties.put(Context.PROVIDER_URL, ldapURL);
            properties.put(Context.SECURITY_AUTHENTICATION, tasProperties.getSecurityAuth());
            properties.put(Context.SECURITY_PRINCIPAL, ldapSecurityPrincipal);
            properties.put(Context.SECURITY_CREDENTIALS, ldapSecurityCredentials);
            searchBase = ldapSearchBase;
            context = new InitialDirContext(properties);
        }
        
        @Override
        public Builder createUser(UserModel user) throws NamingException, UnsupportedEncodingException
        {
            Attributes attributes = new BasicAttributes();
            Attribute objectClass = new BasicAttribute("objectClass", ObjectType.user.toString());
            Attribute sn = new BasicAttribute("sn", user.getLastName());
            Attribute fn = new BasicAttribute("givenName", user.getFirstName());
            Attribute samAccountName = new BasicAttribute("samAccountName", user.getUsername());
            Attribute userAccountControl = new BasicAttribute("userAccountControl");

            userAccountControl.add(Integer.toString(UserAccountStatus.NORMAL_ACCOUNT.getValue() + UserAccountStatus.PASSWD_NOTREQD.getValue()
                    + UserAccountStatus.DONT_EXPIRE_PASSWD.getValue()));

            attributes.put(objectClass);
            attributes.put(sn);
            attributes.put(fn);
            attributes.put(samAccountName);
            attributes.put(userAccountControl);
            context.createSubcontext(String.format(searchBase, user.getUsername()), attributes);

            String newQuotedPassword = String.format("\"%s\"", user.getPassword());
            byte[] newUnicodePassword = newQuotedPassword.getBytes("UTF-16LE");

            ModificationItem[] mods = new ModificationItem[2];
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("unicodePwd", newUnicodePassword));
            mods[1] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("userAccountControl",
                    Integer.toString(UserAccountStatus.NORMAL_ACCOUNT.getValue() + UserAccountStatus.DONT_EXPIRE_PASSWD.getValue())));
            context.modifyAttributes(String.format(searchBase, user.getUsername()), mods);
            return this;
        }

        @Override
        public Builder deleteUser(UserModel user) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Delete user %s", user.getUsername()));
            context.destroySubcontext(String.format(searchBase, user.getUsername()));
            return this;
        }

        @Override
        public Builder updateUser(UserModel user, HashMap<String, String> attributes) throws NamingException, UnsupportedEncodingException {
            STEP(String.format("[LDAP-AD] Update user %s", user.getUsername()));
            ModificationItem[] items = new ModificationItem[attributes.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : attributes.entrySet())
            {
                Attribute attribute = new BasicAttribute(entry.getKey());
                if(entry.getKey().equals("unicodePwd"))
                {
                    String newQuotedPassword = String.format("\"%s\"", entry.getValue());
                    byte[] newUnicodePassword = newQuotedPassword.getBytes("UTF-16LE");
                    attribute.add(newUnicodePassword);
                }
                else
                {
                   attribute.add(entry.getValue());
                }
                items[i] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attribute);
                i++;
            }

            context.modifyAttributes(String.format(searchBase, user.getUsername()), items);
            return this;
        }

        @Override
        public Builder createGroup(GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Create group %s", group.getDisplayName()));
            Attributes attributes = new BasicAttributes();

            Attribute objectClass = new BasicAttribute("objectClass");
            Attribute samAccountName = new BasicAttribute("samAccountName");
            Attribute name = new BasicAttribute("name");

            objectClass.add(ObjectType.group.toString());
            samAccountName.add(group.getDisplayName());
            name.add(group.getDisplayName());

            attributes.put(objectClass);
            attributes.put(samAccountName);
            attributes.put(name);

            context.createSubcontext(String.format(searchBase, group.getDisplayName()), attributes);

            return this;
        }

        @Override
        public Builder deleteGroup(GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Delete group %s", group.getDisplayName()));
            context.destroySubcontext(String.format(searchBase, group.getDisplayName()));
            return this;
        }

        @Override
        public Builder addUserToGroup(UserModel user, GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Add user %s to group %s", user.getUsername(), group.getDisplayName()));
            Attribute memberAttribute = new BasicAttribute("member", String.format(searchBase, user.getUsername()));
            ModificationItem member[] = new ModificationItem[1];
            member[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE, memberAttribute);
            context.modifyAttributes(String.format(searchBase, group.getDisplayName()), member);
            return this;
        }

        @Override
        public Builder removeUserFromGroup(UserModel user, GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Remove user %s from group %s", user.getUsername(), group.getDisplayName()));
            Attribute memberAttribute = new BasicAttribute("member", String.format(searchBase, user.getUsername()));
            ModificationItem member[] = new ModificationItem[1];
            member[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, memberAttribute);
            context.modifyAttributes(String.format(searchBase, group.getDisplayName()), member);
            return this;
        }

        public Builder disableUser(UserModel user) throws NamingException
        {
            Attribute memberAttribute = new BasicAttribute("userAccountControl", Integer.toString(
                    UserAccountStatus.ACCOUNTDISABLE.getValue() + UserAccountStatus.NORMAL_ACCOUNT.getValue() + UserAccountStatus.PASSWD_NOTREQD.getValue()));
            ModificationItem modification[] = new ModificationItem[1];
            modification[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, memberAttribute);
            context.modifyAttributes(String.format(searchBase, user.getUsername()), modification);
            return this;
        }

        public Builder enableUser(UserModel user) throws NamingException
        {
            Attribute memberAttribute = new BasicAttribute("userAccountControl",
                    Integer.toString(UserAccountStatus.NORMAL_ACCOUNT.getValue() + UserAccountStatus.PASSWD_NOTREQD.getValue()));
            ModificationItem modification[] = new ModificationItem[1];
            modification[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, memberAttribute);
            context.modifyAttributes(String.format(searchBase, user.getUsername()), modification);
            return this;
        }

        public SearchResult searchForObjectClass(String name, ObjectType typeOfClass) throws NamingException
        {
            NamingEnumeration<SearchResult> results = null;
            String searchFilter = String.format("(objectClass=%s)", typeOfClass.toString());
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            try
            {
                results = context.search(String.format(searchBase, name), searchFilter, searchControls);
            }
            catch (NameNotFoundException e)
            {
                return null;
            }
            if (results.hasMoreElements())
                return results.nextElement();
            return null;
        }

        public Builder createDisabledUser(UserModel user) throws NamingException
        {
            Attributes attributes = new BasicAttributes();
            Attribute objectClass = new BasicAttribute("objectClass");
            Attribute sn = new BasicAttribute("sn");
            Attribute samAccountName = new BasicAttribute("samAccountName");
            Attribute userPassword = new BasicAttribute("userPassword");
            Attribute userAccountControl = new BasicAttribute("userAccountControl");

            objectClass.add(ObjectType.user.toString());
            sn.add(user.getLastName());
            samAccountName.add(user.getUsername());
            userPassword.add(user.getPassword());
            userAccountControl.add(Integer.toString(
                    UserAccountStatus.NORMAL_ACCOUNT.getValue() + UserAccountStatus.PASSWORD_EXPIRED.getValue() + UserAccountStatus.ACCOUNTDISABLE.getValue()));

            attributes.put(objectClass);
            attributes.put(sn);
            attributes.put(samAccountName);
            attributes.put(userAccountControl);

            context.createSubcontext(String.format(searchBase, user.getUsername()), attributes);
            return this;
        }

        @Override
        public Builder assertUserExists(UserModel user) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Assert user %s exists", user.getUsername()));
            Assert.assertNotNull(searchForObjectClass(user.getUsername(), ObjectType.user));
            return this;
        }

        public UserManageable assertUserDoesNotExist(UserModel user) throws NamingException, TestStepException
        {
            STEP(String.format("[LDAP-AD] Assert user %s does not exist", user.getUsername()));
            Assert.assertNull(searchForObjectClass(user.getUsername(), ObjectType.user));
            return this;
        }

        public GroupManageable assertGroupExists(GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Assert group %s exists", group.getDisplayName()));
            Assert.assertNotNull(searchForObjectClass(group.getDisplayName(), ObjectType.group));
            return this;
        }

        public GroupManageable assertGroupDoesNotExist(GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Assert group %s does not exist", group.getDisplayName()));
            Assert.assertNull(searchForObjectClass(group.getDisplayName(), ObjectType.group));
            return this;
        }

        public Builder assertUserIsDisabled(UserModel user, UserAccountControlValue userAccountControlValue) throws NamingException
        {
            Attributes accountStatus = context.getAttributes(String.format(searchBase, user.getUsername()), new String[] { "userAccountControl" });
            Assert.assertTrue(accountStatus.toString().contains(userAccountControlValue.toString()),
                    String.format("User account control value expected %s but found %s", userAccountControlValue.toString(), accountStatus.toString()));
            return this;
        }

        public Builder assertUserIsEnabled(UserModel user, UserAccountControlValue userAccountControlValue) throws NamingException
        {
            Attributes accountStatus = context.getAttributes(String.format(searchBase, user.getUsername()), new String[] { "userAccountControl" });
            Assert.assertTrue(accountStatus.toString().contains(userAccountControlValue.toString()),
                    String.format("User account value expected %s but found %s ", userAccountControlValue.toString(), accountStatus.toString()));
            return this;
        }

        @Override
        public GroupManageable assertUserIsMemberOfGroup(UserModel user, GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Assert user %s is member of group %s", user.getUsername(), group.getDisplayName()));
            Attributes membership = context.getAttributes(String.format(searchBase, group.getDisplayName()), new String[] { "member" });
            Assert.assertTrue(membership.toString().contains(String.format(searchBase, user.getUsername())),
                    String.format("User %s is not member of group %s", user.getUsername().toString(), group.getDisplayName().toString()));
            return this;
        }

        @Override
        public GroupManageable assertUserIsNotMemberOfGroup(UserModel user, GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Assert user %s is not member of group %s", user.getUsername(), group.getDisplayName()));
            Attributes membership = context.getAttributes(String.format(searchBase, group.getDisplayName()), new String[] { "member" });
            Assert.assertFalse(membership.toString().contains(String.format(searchBase, user.getUsername())),
                    String.format("User %s is member of group %s", user.getUsername().toString(), group.getDisplayName().toString()));
            return this;
        }

        public Builder addBulkUsersInGroups(int noGroups, int noUsersPerGroup) throws NamingException, UnsupportedEncodingException
        {
            STEP(String.format("[LDAP-AD] Add %s groups with %s users in each group", noGroups, noUsersPerGroup));
            HashMap<GroupModel, List<UserModel>> usersGroupsMap = new HashMap<>();
            for (int i = 0; i < noGroups; i++)
            {
                GroupModel testGroup = GroupModel.getRandomGroupModel();
                createGroup(testGroup).assertGroupExists(testGroup);

                List<UserModel> groupUsers = new ArrayList<>();
                for (int j = 0; j < noUsersPerGroup; j++)
                {
                    UserModel testUser = UserModel.getRandomUserModel();
                    testUser.setPassword("Password1234!");
                    createUser(testUser).addUserToGroup(testUser, testGroup);
                    groupUsers.add(testUser);
                }

                usersGroupsMap.put(testGroup, groupUsers);
            }
            return this;
        }

        private SearchResult searchGeneratedData(String partialName, ObjectType typeOfClass) throws NamingException
        {
            NamingEnumeration<SearchResult> results = null;
            String searchFilter = String.format("(&(objectClass=%s)(%s*))", typeOfClass.toString(), partialName);
            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            try
            {
                results = context.search(searchBase.replace("CN=%s,", ""), searchFilter, searchControls);
            }
            catch (NameNotFoundException e)
            {
                return null;
            }
            if (results.hasMoreElements())
                return results.nextElement();
            return null;
        }

        public Builder deleteBulkUsers() throws NamingException
        {
            STEP(String.format("[LDAP-AD] Delete all users which start with 'user-'"));
            SearchResult rez = searchGeneratedData("cn=user-", ObjectType.user);
            while (rez != null)
            {
                context.destroySubcontext(rez.getNameInNamespace());
                rez = searchGeneratedData("cn=user-", ObjectType.user);
            }
            return this;
        }

        public Builder deleteBulkGroups() throws NamingException
        {
            STEP(String.format("[LDAP-AD] Delete all groups which start with 'group-'"));
            SearchResult rez = searchGeneratedData("cn=group-", ObjectType.group);
            while (rez != null)
            {
                context.destroySubcontext(rez.getNameInNamespace());
                rez = searchGeneratedData("cn=group-", ObjectType.group);
            }
            return this;
        }

        public Builder addGroupAsMemberOfAnotherGroup(GroupModel childGroup, GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Add group %s as member of group %s", childGroup.getDisplayName(), group.getDisplayName()));
            Attribute memberAttribute = new BasicAttribute("memberUID", String.format(searchBase, childGroup.getDisplayName()));
            ModificationItem member[] = new ModificationItem[1];
            member[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE, memberAttribute);
            context.modifyAttributes(String.format(searchBase, group.getDisplayName()), member);
            return this;
        }

        public Builder assertGroupIsMemberOfGroup(GroupModel childGroup, GroupModel group) throws NamingException
        {
            STEP(String.format("[LDAP-AD] Assert group %s is member of group %s", childGroup.getDisplayName(), group.getDisplayName()));
            Attributes membership = context.getAttributes(String.format(searchBase, group.getDisplayName()), new String[] { "memberUid" });
            Assert.assertTrue(membership.toString().contains(String.format(searchBase, childGroup.getDisplayName())));
            return this;
        }

        public String getUserId(UserModel userModel) throws NamingException
        {
            String[] DCs = searchBase.split(",DC=");
            return String.format("%s@%s.%s", userModel.getUsername(), DCs[1], DCs[2]);
        }

        public String getUserDCId(UserModel userModel) throws NamingException
        {
            return String.format(searchBase, userModel.getUsername());
        }
    }
}
