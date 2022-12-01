# DDS Permissions Manager

DDS stands for [Data Distribution Service](https://www.omg.org/spec/DDS/).
DDS applications communicate by writing and reading samples which are just pieces of data.
For example, a weather station application may write temperature samples that a weather forecasting application reads.
A topic is a named collection of related samples.
Topics are usually named to describe the data that is being shared.
For example, the topic for temperature samples might be named "Temperature".

A [secure DDS](https://www.omg.org/spec/DDS-SECURITY/) application uses a permissions file that controls its ability to write and read topics.
For example, weather stations should be able to write samples to the Temperature topic and forecasting applications should be able to read samples from the Temperature topic.

The DDS Permissions Manager allows users to enter the information necessary for a permissions file.
After entering the information, an application can download its permissions file and other DDS Security documents to interact with other applications.

Inter-organizational collaboration was one of the main motivations when designing the DDS Permissions Manager.
DDS Security is based on public key infrastructure (PKI).
In the DDS Security model, there is an identity certificate authority (CA) that signs certificates issued to DDS participants for mutual authentication and a permissions CA that signs documents that DDS participants use for mutual authorization.
The DDS Permissions Manager plays the role of the identity and permissions CA.
Organizations using a common DDS Permissions Manager instance can share data using DDS because they are using the same identity and permissions CA.

The principle of collaboration applies within an organization as well.
That is, different groups within an organization can use a private instance of the DDS Permissions Manager to organize the DDS security documents for their DDS applications.
In this scenario, the organization can use the UI to enter information or use the API directly.

## User Documentation

The DDS Permissions Manager is built on the following concepts:
* Application - An application represents a secure DDS application.
* Topic - A topic represents a DDS topic.
* Group Membership - A group membership assigned a user (identified by their email address) zero or more roles in a Group.
* Group - A collection of Applications, Topics, and Group Memberships.
* Roles
  * Group Admin - A group admin can add other Group Admins and Topic and Application Admins to their group.
  * Application Admin - An application admin can create/delete applications and generate passwords for applications.
  * Topic Admin - A topic admin can create/delete topics and grant/revoke access to them.
* Super Admin - A super admin is a user that can perform any operation.

### Groups and Super Admins

A Group is just a unique name that acts as a container for Group Memberships, Topics, and Applications.
That is, a Group is associated with every Group Membership, Topic, and Application.
Deleting a group deletes all of its associated Group Memberships, Topics, and Applications.

A Super Admin has unrestricted access and can perform any operation.
Typically, a Super Admin comes from the organization *operating* the DDS Permissions Manager as opposed to an organization that is *using* the DDS Permissions Manager.
The primary activity of a Super Admin is to enroll organizations that wish to use the DDS Permissions Manager.
In this capacity, a Super Admin creates a Group for the organization and adds at least one Group Admin to the Group.
From there, the Group Admin can complete the onboarding process.
To add the Group Admin, the Super Admin creates a Group Membership that includes the 1) email address of the Group Admin, 2) the Group to which the membership applies, and 3) a set of grants, specifically, the Group Admin grant. 

The activities that can only be performed by a Super Admin are:
* Add/remove other super admins
* Create/delete groups
* Add the initial Group Admin to a Group

### Group Memberships, Roles, and Group Admins

A Group Membership assigns zero or more roles to a user with reference to a specific Group.
The roles define the capabilities of the user with respect to that particular Group.
The three possible roles are Group Admin, Topic Admin, and Application Admin.
A user can have multiple Group memberships; one for each Group to which they belong.
Furthermore, a user can have different roles for each Group.
For example, the same user could be a Group Admin in one Group while they are a Topic Admin in another.
A user can be added to a group without any roles selected.
In this case, the user can see all of the information for a Group but cannot make any changes.

A Group Admin has the responsibility of maintaining the Group Memberships for their Groups.
A Group Admin can add/remove/edit Group Memberships for Groups for which they are Group Admin
To complete the onboarding process, a Group Admin adds Topic Admins and Applications Admins to their group.
The Group Admin can also add other Group Admins.
To add a user to a group, a Group Admin creates/edits a Group Membership that include the user's email address, Group, and roles.

### Applications and Application Admins

An Application represents a secure DDS application.
An Application has a name and belongs to a Group.
An Application also has a password that allows it to authenticate to the API and download its DDS Security documents.
Application Admins can edits the list of Applications in a Group and generate Application passwords.
Generating a new password invalidates the existing one and there is no way show the current password.
Consequently, Application Admins should record and distribute the password in a secure way.

### Topics and Topic Admins

A Topic represents a DDS Topic.
A Topic has a name and belongs to a Group.
A Topic can be configured so that any Application can read it or configured so that the list of reading Applications must be specified.
This cannot be changed after the Topic is created.
Associated with a Topic is a list of Applications and there permissions (read and/or write) for that Topic.
The Application can belong to any Group.
(This allows Applications in different organizations to share data.)
Topic Admins can edit the list of Topics for a Group and edit the access to a Topic.

### Downloading DDS Security Documents

This section describes how an Application authenticates with the DDS Permissions Manager and downloads its security documents.
The process is illustrated using `curl` and assumes that `DPM_URL` is the URL of the DDS Permissions Manager.

1. An Application Admin must generate a password by going to the Application detail screen and clicking "Generate Password."  The Application Admin should also note the "Username" on the same screen.
2. An application requests a JWT from the API.  For, example

        curl -c cookies.txt -H'Content-Type: application/json' -d'{"username":"${USERNAME}","password":"${PASSWORD}"}' ${DPM_URL}/api/login

3. With the JWT stored in `cookies.txt`, the Application can request the Identity CA certificate

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/identity_ca.pem > identity_ca.pem

4. and the Permissions CA certificate

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/permissions_ca.pem > permissions_ca.pem
        
5. and the Governance file

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/governance.xml.p7s > governance.xml.p7s
        
6. The Application can request a key and certificate from the API.  The

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/key-pair?nonce=NONCE > key-pair.json
        
   The response in a JSON object containing the private key and public certificate
    
        {
          "private": "-----BEGIN PRIVATE KEY-----\nMEEC...",
          "public": "-----BEGIN CERTIFICATE-----\nMIICrT..."
        }
   
7. Finally, the Application can request the permissions 

        curl --silent -b cookies.txt ${DPM_URL}/api/applications/permissions.xml.p7s?nonce=NONCE > permissions.xml.p7s

One thing to note in the sequence above is the `NONCE` parameter that is passed in the request for the key pair and permissions document.
The nonce should be an alphanumeric string that identifies a particular Application instance.
The nonce is used to construct the subject in the public certificate and permissions file.
The subject will consist of a common name (CN) that is the nonce appended to the Application's id, a given name (GN) that is the Application name, and a surname (SN) that is the Group id of the Application.
It is possible to run multiple copies (instances) of the same Application by using different nonces.

### Canonical Topic Names

To facilitate inter-organization interactions, the DDS Permissions Manager constructs a *canonical* topic name which is TOPICKIND.GROUPID.TOPIC_NAME.
Canonical topic names appear on the Topic Detail Screen and the Application Detail Screen.
*DDS developers must use the canonical topic name when creating a topic.*

TOPICKIND will be "B" if any Application can read the Topic and "C" if Applications must be explicitly added to read.
The author of the governance file enforces these semantics by writing appropriate rules in the governance file.
For example,

    <topic_access_rules>
        <topic_rule>
          <topic_expression>B*</topic_expression>
          <enable_discovery_protection>true</enable_discovery_protection>
          <enable_liveliness_protection>true</enable_liveliness_protection>
          <enable_read_access_control>false</enable_read_access_control>
          <enable_write_access_control>true</enable_write_access_control>
          <metadata_protection_kind>ENCRYPT</metadata_protection_kind>
          <data_protection_kind>NONE</data_protection_kind>
        </topic_rule>
        <topic_rule>
          <topic_expression>C*</topic_expression>
          <enable_discovery_protection>true</enable_discovery_protection>
          <enable_liveliness_protection>true</enable_liveliness_protection>
          <enable_read_access_control>true</enable_read_access_control>
          <enable_write_access_control>true</enable_write_access_control>
          <metadata_protection_kind>ENCRYPT</metadata_protection_kind>
          <data_protection_kind>NONE</data_protection_kind>
        </topic_rule>

The topic rule with topic expression `B*` matches topic names that start with "B".
The corresponding rule says that the `enable_read_access_control` is false meaning that any application can read topics whose names start with "B."
The topic rule for `C*` specifies that `enable_read_access_control` is true so applications must have explicit access to read topics whose names start with "C."

The TOPICKIND convention is an assumption of the DDS Permissions Manager that allows one to write a generic governance file.
Future versions of the DDS Permissions Manager might provide support for additional topic kinds.

The GROUPID is included in the canonical topic name to disambiguate topics with the same name belonging to different groups.

### Limitations

* The DDS Permissions Manager assumes topic rules for supporting the different topic kinds.  See the Canonical Topic Name section.
* The certificate expiration is configurable at a global level and not for specific applications.
* The DDS Permissions Manager does not allow a free-form permissions file.
* The DDS Permissions Manager does not currently support for all permissions document fields
  * Validity is configurable at a global level and not for specific applications and/or topics.
  * There is no way to specify a list of partitions.

## Operator Documentation

TODO

### Supported Databases

| Database     | Versions     | Driver                   | Reference                                                                                                                 |
|--------------|--------------|--------------------------|---------------------------------------------------------------------------------------------------------------------------|
| MySQL Server | 8.0 and 5.7  | com.mysql.cj.jdbc.Driver | [link](https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-versions.html)                                            |
| PostgreSQL   | 8.2 or newer | org.postgresql.Driver    | [link](https://jdbc.postgresql.org/documentation/#:~:text=The%20current%20version%20of%20the,(JDBC%204.2)%20and%20above.) |

To connect to a database, please set the following environment variables:

* DPM_DATABASE_DEPENDENCY (examples include `mysql:mysql-connector-java:8.0.31` and `org.postgresql:postgresql:42.4.2`)
* DPM_JDBC_URL
* DPM_JDBC_DRIVER (see Driver column for values)
* DPM_JDBC_USER
* DPM_JDBC_PASSWORD
* DPM_AUTO_SCHEMA_GEN (Options include `none`, `create-only`, `drop`, `create`, `create-drop`, `validate`, and `update` (default value))

The following describes the options for DPM_AUTO_SCHEMA_GEN environment variable in detail:

**none** - No action will be performed. 

**create-only** - Database creation will be generated.

**drop** - Database dropping will be generated.

**create** - Database dropping will be generated followed by database creation.

**create-drop** - Drop the schema and recreate it on SessionFactory startup. Additionally, drop the schema on SessionFactory shutdown.

**validate** - Validate the database schema.

**update** - Update the database schema.

### Initial User
Permissions Manager requires an initial super administrator for the purpose of logging in and adding other super admins
or regular users. To do so, please connect to the database which Permissions Manager will connect to and execute the 
following SQL statement:

```sql
INSERT INTO permissions_user (admin, email)
VALUES (true, 'admin-email@GoogleAccount.com');
```
