## Synopsis

This is the home of the **TAS**( **T**est **A**utomation **S**ystem)- **Utility** project.
It is based on Apache Maven, compatible with major IDEs and is using also Spring capabilities for dependency injection.

As a high level overview, this project contains a couple of functionalities usefull for automation testing as: 
* reading/defining test environment settings (e.g. alfresco server details, authentication, etc.)
* utilities (creating files,folders)
* test data generators (for site, users, content, etc)
* helpers (i.e. randomizers, test environment information)
* test reporting capabilities
* test management integration (at this point we support integration with [Test Rail](https://alfresco.testrail.net) (v5.2.1)
* Healthchecks (check if server is reachable, if server is online)
* Generic Internal-DSL (Domain Specific Language)

Using a centralized location (Nexus), everyone will be able to reuse this individual interfaces in their own projects, adding new functionalities, extending also the automation core functionalities - that will be shared across teams. 


## Prerequisite 
(tested on unix/non-unix destribution)
* [Java SE 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html).
* [Maven 3.3](https://maven.apache.org/download.cgi) installed and configure according to [Windows OS](https://maven.apache.org/guides/getting-started/windows-prerequisites.html) or [Mac OS](https://maven.apache.org/install.html).
* Configure Maven to use Alfresco alfresco-internal repository following this [Guide](https://ts.alfresco.com/share/page/site/eng/wiki-page?title=Maven_Setup).
* Your favorite IDE as [Eclipse](https://eclipse.org/downloads/) or [InteliJ](https://www.jetbrains.com/idea).
* Access to [Nexus](https://nexus.alfresco.com/nexus/) repository.
* Access to Gitlab [TAS](https://gitlab.alfresco.com/tas/) repository.
* GitLab client for your operating system. (we recommend [SourceTree](https://www.sourcetreeapp.com) - use your google account for initial setup).
* Getting familiar with [Basic Git Commands](http://docs.gitlab.com/ee/gitlab-basics/basic-git-commands.html).
* Getting familiar with [Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html).
* Getting familiar with [Spring](http://docs.spring.io).

## Installation (if you want to contribute)

* Open your Gitlab client and clone the repository of this project.
* You can do this also from command line (or in your terminal) adding:

```bash
> git clone https://gitlab.alfresco.com/tas/alfresco-tas-tester.git
```

* Install and check if all dependencies are downloaded

```bash
> cd alfresco-tas-tester
> mvn clean install -DskipTests
# you should see one [INFO] BUILD SUCCESS message displayed
```

## Package Presentation

This project uses a simple maven project [archetype](https://maven.apache.org/plugins-archives/maven-archetype-plugin-1.0-alpha-7/examples/simple.html):
```ruby
├── src
│   ├── main
│   │   ├── java
│   │   │   └── org
│   │   │       └── alfresco
│   │   │           └── utility
│   │   │               ├── data # helpers for creating Sites/Files, Users, etc)
│   │   │               │   (...)
│   │   │               ├── dsl
│   │   │               │   ├──(...)
│   │   │               ├── exception # custom exception
│   │   │               │   (...)
│   │   │               ├── model #modeling generic objects that will be reused in test
│   │   │               │   ├── FileModel.java
│   │   │               │   ├── FileType.java
│   │   │               │   ├── FolderModel.java
│   │   │               │   └── UserModel.java
│   │   │               │   └── (...)
│   │   │               ├── network # network based helpers
│   │   │               │   └── (...)
│   │   │               ├── report #handling reporting (i.e. listeners for generating html reports)
│   │   │               │   └── (...)
│   │   │               └── testrail # TestRail integration utils
│   │   │               │   └── (...)
│   │   └── resources
│   └── test
│       ├── java
│       │   └── org
│       │       └── alfresco
│       │           └── utility #testing classes/sample code
│       │               ├── (...)
│       └── resources
│           ├── default.properties #one place where you defined all settings like what alfresco server to use, credentials, etc.
│           ├── log4j.properties
│           ├── testdata #placeholder for holding test data
│           │   └── (...)
```

## Sample Usage

In your maven project, in your pom.xml file add the following dependency
```
<dependency>
			<groupId>org.alfresco.tas</groupId>
			<artifactId>utility</artifactId>
			<version>${tas.utility.version}</version>
</dependency>
```
(where ${tas.utility.version} is the latest verion released on [Nexus](https://artifacts.alfresco.com/nexus/content/groups/internal) internal)

**_NOTE_:** _you can also browse the [samples](samples) folder for simple maven projects that is consumming this library. Just import this existing Maven project in your IDE (if you are using Eclipse follow [this](http://help.eclipse.org/kepler/index.jsp?topic=%2Forg.eclipse.platform.doc.user%2Ftasks%2Ftasks-importproject.htm) guide)_

### Configure your maven project to use tas.utility

**_NOTE_:** _you can also browse the [samples](samples) folder for simple maven projects that is consumming this library. Just import this existing Maven project in your IDE (if you are using Eclipse follow [this](http://help.eclipse.org/kepler/index.jsp?topic=%2Forg.eclipse.platform.doc.user%2Ftasks%2Ftasks-importproject.htm) guide)_

* if you have one [simple maven project](https://maven.apache.org/plugins-archives/maven-archetype-plugin-1.0-alpha-7/examples/simple.html) created, you must add Spring bean capabilities to interact with tas.utility project
	* add dependency to your pom.xml (as indicated [above](#sample-usage)) - _no need for spring bean dependencies, this are downloaded automatically from tas.utility_	
	* import resources in src/test/resources/<your-test-context.xml> (see one example [here](samples/consuming-tas-utility/src/test/resources/alfresco-test-context.xml))
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <beans xmlns="http://www.springframework.org/schema/beans"
    	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
    	xmlns:mongo="http://www.springframework.org/schema/data/mongo"
    	xsi:schemaLocation="http://www.springframework.org/schema/context
              http://www.springframework.org/schema/context/spring-context-3.0.xsd
              http://www.springframework.org/schema/data/mongo
              http://www.springframework.org/schema/data/mongo/spring-mongo-1.0.xsd
              http://www.springframework.org/schema/beans
              http://www.springframework.org/schema/beans/spring-beans-3.0.xsd">
    
    	<context:annotation-config />
    	<context:component-scan base-package="org.alfresco" />
    
    	<import resource="classpath:dataprep-context.xml" />
    	<import resource="classpath*:alfresco-tester-context.xml" />
    </beans>
    ```

* copy [default.properties](src/test/resources/default.properties) to your src/test/resources folder, updating the settings as you want (see one example [here](samples/consuming-tas-utility/src/test/resources/default.properties)).
    * notice that we have properties for server configuration. These are pointing to localhost as default, but feel free to point to any alfresco server that you have already installed (version >=5.1)
    
        ```java
        # Alfresco HTTP Server Settings
        alfresco.scheme=http
        alfresco.server=127.0.0.1
        alfresco.port=8080
        ```
    
* create a simple TestNG test for testing the autowired bean capabilities. (see one example [here](samples/consuming-tas-utility/src/test/java/org/alfresco/sample/SampleTest.java))
	```java
    @ContextConfiguration("classpath:alfresco-smtp-context.xml")
    public abstract class MyTestIsAwesome extends AbstractTestNGSpringContextTests{
    	@Autowired
	    protected ServerHealth serverHealth;

    	@Test
	    public void checkAlfrescoServerIsOnline() throws Exception
    	{
        	serverHealth.assertServerIsOnline();
    	}
    }
	```
	
* optional add your default [log4j](http://logging.apache.org/log4j/2.x/) file. You can use [this](src/test/resources/log4j.properties) example.	
* if settings from default.properties are properly set, after [running](#how-to-run-tests) this test you should see Build Success message. 
* in case you are using the default settings that points to localhost (127.0.0.1) and you don't have Alfresco installed on your machine, you will see one exception thrown by the tests as:
 
    ```java
    org.alfresco.utility.exception.ServerUnreachableException: Server {127.0.0.1} is unreachable.
    ```

### How to write a test

* we are using TestNG framework to drive our test, so please feel free to take a look on [official documentation](http://testng.org/doc/index.html) if you are not yet familiarized with this.
* to view a simple class that is using this utility, just browse on [samples/consuming-tas-utility](samples/consuming-tas-utility/src/test/java/org/alfresco/sample/SampleTest.java)
    * notice the class definition and inheritance value:
    
    ```java
        @ContextConfiguration("classpath:alfresco-test-context.xml")
        public class SampleTest extends AbstractTestNGSpringContextTests 
    ```
    * each utility (from package presentation above) can be annotated with @Autowired keyword in your test in order to initialize it.
    * as a convention, before running your test, check if the test environment is reachable and your alfresco test server is online.
    (this will stop the test if the server defined in your property file is not healthy)

    ```java
        @BeforeClass(alwaysRun = true)
	    public void environmentCheck() throws Exception {
		    serverHealth.assertServerIsOnline();
        }
    ```
    * each test name should express cleary what will do:
    
    ```java
        @Test
        public void adminShouldCreateFolderInSite()
        {
            (...)
        }
        
        @Test
        public void adminCannotCreateSameFolderTwice()
        {
            (...)
        }
    ```
     * Use the assertions provided by this utility (see the "data" package)
     
    ```java
        //here we create a new content in the root location of alfresco
        FolderModel myFolder =dataContent.usingRoot().createFolder("MyTestFolder");
        
        // here we assert that folder exist 
		dataContent.assertContentExist(myFolder);
    ```
    

### How to create new data (files/folder)
* configure your project to use spring (as highlighted above)
* in your test file add:
  ```java
  @Autowired
  protected DataContent dataContent;

  @Test
  public void creatingFolderInRepoLocation()
  {
  /*
  *this call will create folder  'myTest' under '/Sites/mySite/documentLibrary' location
  * using default admin user specified in default.properties file
  */
  dataContent.usingSite("mySite").createFolder("myTest")

  /*
  *this call will create folder 'myTest2' under '/' root folder
  * using default admin user specified in default.properties file
  */
  dataContent.usingRoot().createFolder("myTest2")
  }

  @Test
  public void creatingFolderInRepoLocationWithCustomUsers()
  {
  /*
  *this call will create folder  'myTest' under '/Sites/mySite/documentLibrary' location
  * using default user'testUser'
  */
  dataContent.usingUser(testUser).usingSite("mySite").createFolder("myFolderWithUser")

  /*
  *this call will create folder 'myTest2' under '/' root folder
  * using user testUser
  */
  dataContent.usingUser(testUser).usingRoot().createFolder("myFolderWithUser")
  }
  ```

* remember models defined above in the package presentation ? Those models can be used in the utilities presented above. So 'testUser' for example is a UserModel class that can be defined as:
  ```java
  UserModel testUser = new UserModel("testUserName", "testUserPassword")
  ```
  We also have some generators that will ease your code style:
  ```java
  // this will create a new UserModel class using the default admin user defined in default.properties file
  UserModel testUser = dataContent.getAdminUser();
  ```

  ```java
  // this will create a new random user in repository and return a new UserModel class
  @Autowired
  DataUser dataUser;
  //(...)
  UserModel testUser = dataUser.createRandomTestUser();
  ```
### How to create a new site
* configure your project to use spring (as highlighted above)
* in your test file add:
  ```java
  @Autowired
  protected DataSite dataSite
  
  @Test
  public void createSite()
  {
  	// this will create a new public random site using admin (no user provided as we see bellow)
  	dataSite.createPublicRandomSite()
    UserModel testUser = dataUser.createRandomTestUser();
    dataSite.createPublicRandomSite()
  }
  ```

## How to run tests

### -using TestNG Suite
* If you are using Eclipse, and you already configured Eclipse to use [TestNG pluging](http://testng.org/doc/eclipse.html), just right click on the testNG class that you created (something similar to [SampleTest.java](samples/consuming-tas-utility/src/test/java/org/alfresco/sample/SampleTest.java)) select Run As - TestNG Test
  You should see your test passed:

  ![](docs/pics/success-report-eclipse.png)

### -from command line

* In terminal or CMD, navigate (with CD) to root folder of your project (you can use the sample project):

  Based on pom.xml setting, the default suite that is executed is pointing to <suiteXmlFile>src/test/resources/sanity-suite.xml</suiteXmlFile>
  Please analyse this xml file! 
  Notice that only tests that are marked with "sanity" within package "org.alfresco.sample" are executed.
  
  In terminal now type:
  ```bash
  mvn test    
  ```
  
## Test Results
  We already executed a couple of tests using command line as indicated above. Sweet! Please take a look at [sanity-suite.xml](samples/consuming-tas-utility/src/test/resources/sanity-suite.xml) one more time.
  You will see there that we have one listener added:
  
  ```java
  <listener class-name="org.alfresco.utility.report.ReportListenerAdapter"></listener>
  ```
  This will tell our framework, after we run all tests, to generate one HTML report file with graphs and metrics.
  
  Take a look at the targe/reports folder (created after running the tests, path that is also configured in default.properties).
  Open the report.html file.
  
  ![](docs/pics/html-report-sample.png)
  
  Playing with this report, you will notice that you will be able to:
    * search tests cases by name
    * filter test cases by errors, labels, groups, test types, date when it was executed, protocol used, etc.
    * view overall pass/fail metrics of current test suite, history of tests execution, etc.
  
## Test Rail Integration

TBD

## Reference

TBD

## Contributors

As contributors and maintainers of this project, we pledge to respect all people who contribute through reporting issues, posting feature requests, updating documentation, submitting pull requests or patches, and other... [more](CODE_OF_CONDUCT.md)

## License

TBD