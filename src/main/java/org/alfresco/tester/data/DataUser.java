package org.alfresco.tester.data;

import org.alfresco.dataprep.UserService;
import org.alfresco.tester.TasProperties;
import org.alfresco.tester.exception.DataPreparationException;
import org.alfresco.tester.model.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.testng.Assert;

/**
 * Data Preparation for Users
 * 
 * @author Paul Brodner
 */
@Service
public class DataUser extends TestData
{
    @Autowired
    private UserService userService;

    static String USER_NOT_CREATED = "User %s  not created";

    /**
     * Creates a new random user on test server defined in {@link TasProperties}
     * file.
     * 
     * @param userName
     * @return
     * @throws DataPreparationException
     */
    public UserModel createUser(String userName) throws DataPreparationException
    {
        UserModel newUser = new UserModel(userName, PASSWORD);
        LOG.info("Create user {}", newUser.toString());
        Boolean created = userService.create(properties.getAdminUser(), properties.getAdminPassword(), userName, PASSWORD, String.format(userName, EMAIL),
                String.format("%s FirstName", userName), String.format("LN-%s", userName));
        if (!created)
            throw new DataPreparationException(String.format(USER_NOT_CREATED, newUser.toString()));

        return newUser;
    }

    public void assertUserExist(UserModel user)
    {
        LOG.info("Check user {} exist in repository", user.toString());
        Assert.assertTrue(userService.userExists(properties.getAdminUser(), properties.getAdminPassword(), user.getUsername()),
                String.format("User {} exist in repository", user.toString()));
    }

    public void assertDoesNotUserExist(UserModel user)
    {
        LOG.info("Check user {} does not exist in repository", user.toString());
        Assert.assertFalse(userService.userExists(properties.getAdminUser(), properties.getAdminPassword(), user.getUsername()),
                String.format("User {} exist in repository", user.toString()));
    }
}
