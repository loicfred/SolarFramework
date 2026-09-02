# SolarFramework

A framework for built on Springboot that implements additional tools and shortcuts for development.
It contains pre-made Spring components, beans as well as plenty of utility features.


# 1. AI Utilities
`AIAPI` holds the interfaces and the conversation loop, `AIImpl` the Spring AI backend — add both.
An `IAIService` is one endpoint and one model; a `Chatbot` is an agent using one; a `Conversation`
is a transcript plus the loop that adds to it. Tools are declared with `@AITool`, so a toolbox needs
no Spring AI on its classpath.

## 1.1. Getting Started
See [docs/AI.md](docs/AI.md) for the full tutorial.
- DefaultAIService – the endpoint and model. It does not talk: `createChatbot()…build()` gives you something that does.
- bot.prompt() / chooseBetween() / stream() – one-turn conversations, tools and all.
- bot.startConversation() – a transcript that keeps going, one per user.
- conversation.send() – One turn that answers. `run()` is the same loop, reported step by step.
```java
import static org.solarframework.ai.spring.AIRegistry.DefaultAIService;

@SpringBootApplication
public class Main {
    static void main(String[] args) {
        SpringApplication.run(Main.class, args);

        Chatbot quick = DefaultAIService.createChatbot().build(); // a plain bot on that endpoint, reusable

        List<String> outputs = quick.chooseBetween("Which of these fruits are red?", List.of("Apple", "Cherry", "Banana", "Orange"));
        System.out.println(outputs); // [Apple, Cherry]

        System.out.println(quick.prompt("Hi, how are you?")); // Hi, how can I help you today?
        System.out.println(quick.prompt("What is the current time?", new Toolbox()));

        Chatbot bot = DefaultAIService.createChatbot().name("timekeeper")
                .systemPrompt("You are an assistant that answers time-related questions.")
                .tools(new Toolbox()).maxSteps(5).build();

        Conversation c = bot.startConversation("conversation-1", "Loïc");
        System.out.println(c.send("What is the current time?")); // The current time is XXX
        System.out.println(c.send("Thank you!"));
        c.save(); // only now does it touch the database
    }

    public static class Toolbox {
        @AITool(description = "Returns the current date and time.")
        public String getCurrentTime() {
            return LocalDateTime.now().toString();
        }
    }
}
```

# 2. Database Utilities
A database dependency that provides caching mechanisms and database service functions to enhance database operations in Spring applications.

- **Integrated Caching**: Each database operation is cached for optimal performance till an update of that object occurs.
- **Single-query Joins**: Allowing returning an object with all their joint objects in one query.

## 2.1. Getting Started
The first thing to do after adding the dependency is making sure caching is enabled in your spring application.
You must also scan both your package and the package of the library.
```java
@EnableCaching
@SpringBootApplication
@ComponentScan({"my.app.spring", "my.loic.utilities.*.spring"})
public class MyApplication {
    static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
        // Your code here...
    }
}
```

## 2.2. Creating a Database Object class
The `DatabaseObject<>` class is the base class for all database objects.
They must extend either `DatabaseObject<>` or `DatabaseObject.ID_OBJ<>` which takes your object class as generics.
The `DatabaseObject.ID_OBJ<>` class is used to provide a default ID field to your object to avoid you from declaring an ID field to all your classes.
You must annotate your table and field using Jakarta annotations.

**Option 1:** Extend `DatabaseObject<>` and annotate with Jakarta.
```java
import jakarta.persistence.*;

@Table
public class User extends DatabaseObject<User> {
    @OneToMany
    @JoinColumn(referencedColumnName = "UserID", name = "UserID")
    private transient List<Order> orders;
    
    @Id
    private Long UserID;
    private String Name;
}
```

**Option 2:** Use `DatabaseObject.ID_OBJ<>` to have an ID by default to avoid redundancy.
```java
import jakarta.persistence.*;

@Table(name = 'authAccountUser') 
public class User extends DatabaseObject.ID_OBJ<Long, User> {
    @OneToMany
    @JoinColumn(referencedColumnName = "ID", name = "UserID")
    private transient List<Order> orders;
    
    private String Name;
}
```

The `DatabaseObject` provides various utility functions to interact with the object.
- **Object Functions**:
    - `Write()` Insert the current object into the database.
    - `WriteThenReturn()` Insert the current object into the database then returning it as an object.
    - `Upsert()` Insert else update the current object into the database.
    - `Upsert()` Insert else update the current object into the database then returning it as an object.
    - `Update()` Updates the current object with the provided values.
    - `UpdateOnly()` Updates only selected columns.
    - `IncrementColumn()` Increase the value of a column by a given amount.
    - `refetchAttribute()` Get a field value of an attribute from the database (lazy loading).

## 2.3. Connect to your database
The following example shows how to connect to a database using the service.
You must add the following properties to your `application.properties` file of springboot.
```properties
spring.datasource.url=DATABASE_URL
spring.datasource.username=USERNAME
spring.datasource.password=PASSWORD
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.pool-name=MyHikariPool
```

## 2.4. Interact with your database
The following example shows using the service in practice.
Note: You must enable caching in your spring application. You must scan both your package and the package of the library.
```java
import static my.loic.utilities.db.spring.DatabaseService.dbService;

public void interactTest() {
   User authAccountUser = dbService.getById(User.class, 3L).orElse(null);
   authAccountUser.Name = "Loic";
   authAccountUser = authAccountUser.WriteThenReturn();

   User loic = dbService.getWhere(User.class, "Name = ?", "Loic").orElse(null);
   loic.Email = "loic@email.com";
   loic.UpdateOnly("Email");

   Row R = dbService.doQuery("SELECT * FROM authAccountUser WHERE Email = ?", "loic@email.com").orElse(null);
   String email = R.getAsString("Email");   
   
   loic.Delete();
}
```

These are the service functions used to fetch and update items.
- **Service Functions**:
    - `dbService.getById()` Get a single row by id and map to a class.
    - `dbService.getByIdWithJoins()` Get a single row by id and map to a class with all join columns.
    - `dbService.getWhere()` Get a single row by condition and map to a class.
    - `dbService.getWhereWithJoins()` Get a single row by condition and map to a class with all join columns.
    - `dbService.getAll()` Get many rows and map them to a class.
    - `dbService.getAllWhere()` Get many rows by condition and map them a class.
    - `dbService.doUpdate()` Perform an update operation.
    - More...

# 3. Discord Utilities
A Discord dependency that provides a simple interface to interact with the Discord API. It makes use of JDA libraries.

## 3.1. Getting Started
You must use `BotBuilder` to configure the bot. You must include the bot token as well as the package when you are going to declare all your interactions (commands).  
The following example shows how to set up the bot.
```java
public void setupBot() {
    BotBuilder BB = new BotBuilder("TOKEN", "my.discord.bot.interaction");
    BB.setBotGuild(930718276542136400L); // A guild used by the bot.
    BB.setLogChannel(1121096901681483807L); // A channel where bot logs will be sent in the bot guild.
    BB.setTemporaryFilesChannel(1102661048269541406L); // A channel where bot temporary files will be sent in the bot guild.
    BB.setAfterReadyAction(() -> {
        // Actions done after the bot is ready.         
    });
    BB.build();   
}
```
## 3.2. Demonstration
Below is a command demonstration. This is available for all types of discord interaction (Button, StringSelect, Modal, etc...).
The following example shows how to create a slash command with a button attached to it which displays metadata.
```java
package my.discord.bot.interaction;

import org.solarframework.discord.core.*;
import org.solarframework.discord.core.annotation.*;

// This is a slash command /hello that has a button "Click me!" attached to it with metadata attached.
@SlashCommand(name = "hello", description = "Says hello to the authAccountUser.")
public class SlashHello extends SlashCMD {
    @Override
    public void onSlash(SlashCommandInteractionEvent e) {
        Button Btn = makeButton(ClickMe.class, e, "This is some data."); // makeButton is part of SlashCMD
        e.reply("Hello, " + e.getUser().getAsMention() + "!").setComponents(ActionRow.of(Btn)).queue();
    }
}

// This is the button attached to the slash command above. It responds with the metadata when clicked.
@ButtonCommand(id = "click_me", label = "Click me!")
public class ClickMe extends ButtonCMD {
    @Override
    public void onPressed(ButtonInteractionEvent e, String[] metadata) {
        e.reply("Here's the data: " + metadata[0]).queue();
    }
}
```

# 4. SSL Server Utilities
A useful web control server that allows users to setup local HTTPS and reverse proxies easily.

## 4.1. Getting Started
You must use `SSLBuilder` to configure the server. You can register as many domains and subdomains as you want with their own headers and customized files path.
```java
@SpringBootApplication
public class Main {

    static void main(String[] args) throws Exception {
        SpringApplication.run(Main.class, args);
        setupProxy();
    }

    private static void setupProxy() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");

        Domain H1 = new Domain(SSLBuilder.LOCALHOST, "mysite.com").headers(headers);
        H1.addSubdomain("www").headers(headers);
        H1.addSubdomain("admin").headers(headers);
        H1.addSubdomain("accounts").headers(headers);

        Domain H2 = new Domain(SSLBuilder.LOCALHOST, "test2.com", "C:/Website/test2/");
        H2.addSubdomain("www", "C:/Website/test2_www/");

        Domain H3 = new Domain(SSLBuilder.LOCALHOST, "myothersite.com", "http://localhost:8080");

        SSLBuilder builder = new SSLBuilder()
                .registerDomains(H1, H2, H3)
                .regenerateCerts();
        builder.build();
    }
}
```

The default path of files are stored the root ./config/domains. The root domain files (ex. `mysite.com`) are stored in "mysite.com/_" and subdomains files (ex. `www.mysite.com`) are stored in "mysite.com/_www".  
The url `http://localhost:8080` is another spring web application which can use the domain `myothersite.com` for free local HTTPS access.

<img width="600" alt="image" src="https://github.com/authAccountUser-attachments/assets/b4bd2b06-fb33-49d4-ad9c-0e95069c024d" />

# 5. Excel Utilities
A simple library which enables users to convert excel files to database tables.
```java
import org.solarframework.excel.ExcelConverter;
public class Main {

    static void main(String[] args) throws Exception {
        ExcelConverter EC = new ExcelConverter("input.xlsx", "jdbc:mysql://00.00.00.00:3306/myschema");
        EC.withDrop(true); // Enable drop of tables based on sheets
        EC.withCreate(true); // Enable creation of tables based on sheets
        EC.withRows(true); // Enable insert of rows based on sheets
        EC.Convert();
    }
}
```

--- 

## Requirements
- Java 25 or higher
- Springboot project
- MariaDB (Database)
- LM Studio (AI)

## Installation
Add this Maven dependency to your Spring project:

```xml

<parent>
  <groupId>org.solarframework.pluginorg.solarframework.plugin</groupId>
  <artifactId>SolarFramework</artifactId>
  <version>1.0</version>
</parent>
```
Available Modules — `*API` holds the interfaces, `*Impl` the implementation, so add both of a pair:
```xml
<artifactId>core</artifactId>
<artifactId>DatabaseAPI</artifactId>      <artifactId>DatabaseImpl</artifactId>
<artifactId>AIAPI</artifactId>            <artifactId>AIImpl</artifactId>
<artifactId>SearchAPI</artifactId>        <artifactId>SearchImpl</artifactId>
<artifactId>TournamentAPI</artifactId>    <artifactId>TournamentImpl</artifactId>
<artifactId>ProxyServerAPI</artifactId>   <artifactId>NettyProxyServerImpl</artifactId>
<artifactId>SpringProxyServerImplV1</artifactId>  <artifactId>SpringProxyServerImplV2</artifactId>
<artifactId>Excel</artifactId>            <artifactId>JSON</artifactId>
<artifactId>Plugin</artifactId>           <artifactId>Language</artifactId>
<artifactId>WebUtils</artifactId>         <artifactId>DatabaseEditor</artifactId>
<artifactId>Discord</artifactId>          <artifactId>CertificateGenerator</artifactId>
```
