# keycloak-custom-spi-event-azure

## About the SPI
In this repository you'll find a custom SPI for [Keycloak](https://www.keycloak.org/), also for the Enterprise version [RHSSO](https://access.redhat.com/products/red-hat-single-sign-on/) as well. The SPI exports all the events to an Azure Event Hub instance.

## Pre requisites
To be able to export the events, you'll need an Azure Event Hub. You can find more information [here](https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-create) how to create an instance.

Also you'll need a local installation of Keycloak or RHSSO. Or an Openshift environment to run RHSSO in container.

## How to use it
You can use the SPI in two ways:

1. With a local installation of Keycloak or RHSSO;
2. Deployed in a Keycloak or RHSSO Docker image running in a [Openshift](https://access.redhat.com/products/red-hat-openshift-container-platform/).

Deploying the SPI locally
----

Before deploying the SPI, we need to configure some environment variables for the SPI to know how and where to send the events.

The SPI was configured to read **three** environment variables that contains the connection string and the event hub name. The reason to use environment variable is to facilitate the deploy on Openshift as a conteiner.

The environment variables are:

- **AZURE_EVENT_HUB_CONNECTION_STRING** -> represents the connection string to connect with an Azure Event Hub. You can find more information how to get the connection string [here](https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-get-connection-string).
- **AZURE_EVENT_HUB_NAME** -> is the event hub name that was created on Azure. More infos [here](https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-create).
- **AZURE_EVENT_HUB_ADMIN_NAME** -> this is an optional configuration. If you want to export the admin events to a separate event hub, you should configure this environment with the name of the admin event hub name. Otherwise all the admin events will be sent to the same event hub with the other events. 
  - ***PS***: the connection string used to sent events to the admin event hub is the same used in normal events.

To set these environment variables in Linux/MacOS:

```Bash
export AZURE_EVENT_HUB_CONNECTION_STRING=<your_connection_string_here>
export AZURE_EVENT_HUB_NAME=<your_event_hub_name_here>
export AZURE_EVENT_HUB_ADMIN_NAME=<your_event_hub_admin_name_here>
```

Remember to persist these environment variable in your bash profile in order to keep it.

To verify if the environment variable was setted correct, you can run:

```Bash
export | grep AZURE
```

Clone this repo:

```Bash
git clone https://github.com/gferreir/keycloak-custom-spi-event-azure.git

cd keycloak-custom-spi-event-azure
```

Build the SPI:

**PS**: Don't forget to configure your settings.xml with the Red Hat maven repository URL `https://maven.repository.redhat.com/ga/`

```Bash
mvn clean package 
```

Copy the `jar` file to the Keycloak or RHSSO installation to deploy the SPI. Assuming the installation location as `$KEYCLOAK_HOME`:

```Bash
cp target/custom-spi-event-azure.jar $KEYCLOAK_HOME/standalone/deployments
```

Start the Keycloak or RHSSO server normally and access the administration console to change the event listener. Exclude the default event listener `jboss-logging`.

![](configs/imgs/keycloak_change_event_listener.png)

A new option should be able to select `event-hub-event-listener`. Select and save.

![](configs/imgs/keycloak_change_event_listener_custom.png)

From now all the events will be sent to the Azure Event Hub. You'll be able to see on logs the events:

![](configs/imgs/keycloak_events_log.png)

Deploying the SPI on Openshift
----

To deploy this SPI in a Docker Image on Openshift is "almost" the same compared with local deploy. We need to put the `jar` file in the `$KEYCLOAK_HOME/standalone/deployments`, but this time the operation should be done inside of a Docker Image.

I will demonstrate the deploy on Openshift using the RHSSO. So, if you want to do the same, I'm assuming that you have an operational Openshift environment (can be a [CodeReady Container](https://access.redhat.com/documentation/en-us/red_hat_openshift_local/2.3/html/getting_started_guide/index) as well) and all the necessary configurations to be able to pull the Red Hat images from the oficial Red Hat registry.

To facilitate the deploy a custom RHSSO template was created extending a default RHSSO + PostgreSQL template. You can find this custom template [here](configs/sso75-ocp4-x509-postgresql-persistent-custom-spi.yaml) in this repository. The modifications made are to inject the project source to be built and  put the `jar` file in the correct directory.

To deploy a instance of RHSSO with this custom SPI on Openshift run the following snippet code:

```Bash
oc new-app -f configs/sso75-ocp4-x509-postgresql-persistent-custom-spi.yaml \
-p SSO_ADMIN_USERNAME=admin 
-p SSO_ADMIN_PASSWORD=admin 
-p MAVEN_MIRROR_URL=<external_repository_Nexus>
-p GIT_SPI_URL=https://github.com/gferreir/keycloak-custom-spi-event-azure.git
-p AZURE_EVENT_HUB_CONNECTION_STRING= <your_connection_string_here>\
-p AZURE_EVENT_HUB_NAME=<your_event_hub_name_here> \
-p AZURE_EVENT_HUB_ADMIN_NAME=<your_event_hub_admin_name_here> # OPTIONAL
```

Above code explanation:

- An instance of `(RHSSO + Custom SPI) + PostgreSQL`
- The admin user and password is `admin`
- Inform an external repository (Like a Nexus) where all the dependencies can be found
- This repository URL where contains the source code and a `Containerfile` with the instructions to build the final image
- The Azure parameters with the respective configurations

Navigate to the RHSSO address and access the administration console to change the event listener. Exclude the default event listener `jboss-logging`.

![](configs/imgs/keycloak_change_event_listener.png)

A new option should be able to select `event-hub-event-listener`. Select and save.

![](configs/imgs/keycloak_change_event_listener_custom.png)


From now all the events will be sent to the Azure Event Hub. You can access the pod logs on Openshift to see the events:

![](configs/imgs/keycloak_ocp_logs.png)

Other informations
----

- The changes made on the new template are generic enough to deploy any SPI. You can take a look at [Containerfile](Containerfile) and [template](configs/sso75-ocp4-x509-postgresql-persistent-custom-spi.yaml) to see all the steps to build the custom image.
- Also I made a simple application to consume the events from an Azure Event Hub (can be the same that the SPI exports to). You can take a look [here](https://github.com/gferreir/consumer-azure-event-hub).
- This is an initial version. So you can fork this repository and make a Pull Request **(:**

Other **important** information
----

A big thanks to @viniciusscf [(his github profile)](https://github.com/viniciusfcf) to the initial implementation and idea.