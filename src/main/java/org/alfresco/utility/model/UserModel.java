package org.alfresco.utility.model;

public class UserModel extends TestModel
{
    private String username;
    private String password;
    private String domain;

    public UserModel(String username, String password)
    {
        this.setUsername(username);
        this.setPassword(password);
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getDomain()
    {
        return domain;
    }

    public void setDomain(String domain)
    {
        this.domain = domain;
    }
}