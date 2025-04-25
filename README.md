## Getting Started

Welcome to the VS Code Java world. Here is a guideline to help you get started to write Java code in Visual Studio Code.

## Folder Structure

The workspace contains two folders by default, where:

- `src`: the folder to maintain sources
- `lib`: the folder to maintain dependencies

Meanwhile, the compiled output files will be generated in the `bin` folder by default.

> If you want to customize the folder structure, open `.vscode/settings.json` and update the related settings there.

## Dependency Management

The `JAVA PROJECTS` view allows you to manage your dependencies. More details can be found [here](https://github.com/microsoft/vscode-java-dependency#manage-dependencies).


## how to run project

javac --module-path "C:\Java\javafx-sdk-23.0.2\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media -d bin src/*.java

java -cp "bin;lib/mysql-connector-j-9.2.0.jar" Server



javac --module-path "C:\Java\javafx-sdk-23.0.2\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media -d bin src/ClientFX.java

java --module-path "C:\Java\javafx-sdk-23.0.2\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media -cp bin ClientFX




## after adding encryption
javac --module-path "C:\Java\javafx-sdk-23.0.2\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media -cp "lib/bcprov-jdk18on-1.80.jar" -d bin src/*.java

java -cp "bin;lib/mysql-connector-j-9.2.0.jar;lib/bcprov-jdk18on-1.80.jar" Server

java --module-path "C:\Java\javafx-sdk-23.0.2\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media -cp "bin;lib/bcprov-jdk18on-1.80.jar" ClientFX
