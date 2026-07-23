# CCMed: Metamorphic Testing Framework for Constraint Checking Implementations

**CCMed** is an automated testing framework designed to test constraint checking implementations. It is the artifact of the paper *"Compositional Constructive Metamorphism for Testing Constraint Checking Implementations"* presented at the 37th International Symposium on Software Reliability Engineering (ISSRE 2026, forthcoming).
It leverages the core structure of Primitive Units, building blocks extracted from executed source test cases, to generate follow-up test cases. That makes a traditional MR catalog unnecessary, as the framework can automatically generate diverse and complicated follow-up test cases.


## 🚀 Getting Started

### Prerequisites

*   **Java Development Kit (JDK) 21** or higher.
*   **Gradle** (Wrapper included).

### Installation

1.  Clone the repository:
    ```bash
    git clone https://github.com/your-repo/CCTester.git
    cd CCTester
    ```

2.  Build the project:
    ```bash
    ./gradlew build
    ```

### Configuration

The execution is controlled by `config.json`. Key parameters include:

*   `taskName`: Identifier for the test run.
*   `sourceLimit`: Number of source cases to generate.
*   `followUpLimit`: Number of follow-up cases to generate.
*   `workerType`: The type of SUT worker (e.g., `infuse`, `cmid`).
*   `targetPath`: Path to the SUT executable or library.
*   `rulesPath`: Directory containing XML rule templates.

Example `config.json`:
```json
{
  "taskName": "test_run_01",
  "ART": true,
  "sourceLimit": 500,
  "followUpLimit": 1000,
  "workerType": "infuse",
  "targetPath": "cc_utils/INFUSE_ORI/531.jar",
  "rulesPath": "rules/level3all"
}
```

### Usage

#### CLI Mode
Run the tester using the default configuration (`config.json`):
```bash
./gradlew run
```

Or specify a custom configuration file:
```bash
./gradlew run --args="-c my_config.json"
```


## 📄 Citation

*(Note: Citation details to be updated upon publication)*

