# RegistrationUtils

RegistrationUtils is a Gradle plugin providing registration utilities for Minecraft multi-loader projects.

## How does it work

We all know that most people don't want to have a hard dependency on another mod, and since the utilities provided by
this library consist in a few classes, it definitely isn't worth it to have a hard dependency. And as such, this library
is provided in a Gradle plugin form, that installs the RegistrationUtils dependency, and shadows it at the same time in
your mod jars. The steps that the plugin makes:

- unpack and relocate the compiled library
- add the library as a dependency to the subprojects (Common, Forge and Fabric)
- make the `jar` task include the library in the final jar.
  **Do not worry about licensing!** The library is MIT licensed, and the plugin makes sure that an original copy of the
  license is included in the final jar, so you're clear.

## Installing and configuring

To start, in your root `build.gradle`, add the following lines in order to install the plugin:

```groovy
plugins {
    id 'com.matyrobbrt.mc.registrationutils' version "$regVersion" // The plugin is located at the Gradle plugin portal
    // The latest reg version can be found at https://plugins.gradle.org/plugin/com.matyrobbrt.mc.registrationutils
}
```

Next, we need to configure it. That is done in the `registrationUtils` block. We will first relocate the library group
to our group:

```groovy
group 'com.example.examplemod.registration' // Now the library is relocated to `com.example.examplemod.registration`
```

Next we want to declare our projects, and what loader they're for. For this step, we will add the following block in the
configuration of the plugin:

```groovy
projects {
    // A project is declared like so: name { configuration }
    Fabric { type 'fabric' } // The fabric project
    Forge { type 'forge' } // The forge project
    Common { type 'common' } // The common project
}
```

And with that, we're done with the basic configuration of the plugin!
You can now reload Gradle and you should have access to the library.

The full example from above:

```groovy
registrationUtils {
    group 'com.example.examplemod.registration'
    projects {
        Fabric { type 'fabric' } // The fabric project
        Forge { type 'forge' } // The forge project
        Common { type 'common' } // The common project
    }
}
```

## Jar inclusion

By default, Reg shadows itself into the `jar` task (behaviour that can be changed by setting `addsDependencies` to
false, but that means that you will need to configure the gradle dependencies yourself). If you want to shade Reg into
another jar, you need to use the `reg.configureJarTask(Object)` method. 

Example that shades Reg into the `shadowJar` task:
```groovy
reg.configureJarTask(shadowJar)
```