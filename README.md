# COE 619 Project: Energy-Aware Predictive VM Consolidation

This repository contains the implementation of the project "Energy-Aware Predictive VM Consolidation for Reducing Energy Consumption and SLA Violations in Cloud Data Centers," submitted for the COE 619-03 course at KFUPM.

## Project Overview

Traditional reactive Virtual Machine (VM) consolidation algorithms in cloud data centers initiate migrations only *after* a physical host becomes overloaded (reaching 100% CPU utilization). This causes unavoidable Service Level Agreement (SLA) violations while the migration takes place. 

This project explores a **Predictive Approach** that uses lightweight forecasting techniques to anticipate CPU spikes and migrate VMs *before* the host reaches saturation. 

### Key Objectives:
1. **Reduce SLA Violations:** Preemptively avoid host overload states.
2. **Minimize Energy Consumption:** Optimize VM placement to keep the maximum number of hosts in sleep mode.
3. **Keep Overhead Low:** Prove that lightweight prediction algorithms can balance energy savings and SLA compliance without the massive processing overhead of heavy Deep Learning models.

## Technology Stack
- **Language:** Java (JDK 11+)
- **Simulation Framework:** [CloudSim Plus](https://cloudsimplus.org/) (a modernized, highly extensible fork of CloudSim 3/4)
- **Dependency Management:** Apache Maven
- **Workloads:** Real-world PlanetLab CPU utilization traces

## Repository Structure
- `src/main/java/` : Contains the core Java simulation code, predictive models, and power management logic.
- `pom.xml` : Maven configuration file managing project dependencies.

## Setup Instructions
1. Ensure Java (JDK 11 or higher) and Maven are installed on your system.
2. Clone this repository.
3. Run `mvn clean install` to download dependencies and build the project.
4. Run the main simulation class from your preferred Java IDE (Eclipse, IntelliJ).
