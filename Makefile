SHELL := /bin/sh
.DEFAULT_GOAL := help

# Which product flavor to build. The project declares core/foss/gplay; the bare
# assemble<BuildType>/install<BuildType> tasks either build all three flavors at once
# (assembleDebug) or do not exist at all (installDebug), so every target below is
# flavor-qualified. Override with e.g. `make FLAVOR=core debug`.
FLAVOR ?= foss

# Gradle wrapper
GRADLE := ./gradlew

# JDK discovery, in order of preference:
#   1. JAVA_HOME from the environment, if it already points at a usable JDK
#   2. macOS: java_home, which knows about /Library/Java/JavaVirtualMachines
#   3. Linux/WSL: probe /usr/lib/jvm, picking the first JDK new enough
#   4. whatever javac is on PATH
# AGP needs JDK 17 or newer; 17, 21 and 25 all build this project. Source and target
# compatibility stay pinned in gradle/libs.versions.toml, so the bytecode does not
# depend on which of them you use.
MIN_JDK := 17

# $(call jdk_major,<home>) -> major version, or empty if that is not a JDK at all.
# The -x test keeps the shell from printing "not found" straight to the terminal.
jdk_major = $(shell [ -x "$(1)/bin/javac" ] && "$(1)/bin/javac" -version 2>&1 | sed -e 's/^javac //' -e 's/[.+_-].*//')
jdk_ok = $(shell [ "$(call jdk_major,$(1))" -ge $(MIN_JDK) ] 2>/dev/null && echo ok)

# `make help` has to work before a JDK is installed, so skip discovery entirely for it.
JDK_NEEDED := $(filter-out help,$(or $(strip $(MAKECMDGOALS)),$(.DEFAULT_GOAL)))

ifneq ($(strip $(JDK_NEEDED)),)

# An inherited JAVA_HOME that is too old is common (distro default, old shell profile).
# Warn and keep looking rather than failing, since a newer JDK is usually installed too.
ifneq ($(strip $(JAVA_HOME)),)
  ifneq ($(call jdk_ok,$(JAVA_HOME)),ok)
    $(warning ignoring JAVA_HOME=$(JAVA_HOME): not a JDK $(MIN_JDK)+)
    JAVA_HOME :=
  endif
endif

# Darwin / Linux / MINGW*_NT / CYGWIN*_NT. Everything non-Darwin tries the Linux layout
# first and then falls back to PATH, which is what MSYS and Cygwin need anyway.
UNAME_S := $(shell uname -s)

ifeq ($(strip $(JAVA_HOME)),)
  ifeq ($(UNAME_S),Darwin)
    JAVA_HOME := $(shell /usr/libexec/java_home -v $(MIN_JDK)+ 2>/dev/null)
  else
    # do not sort these lexically: java-8-openjdk sorts after java-21-openjdk, so ask
    # each candidate for its version instead of guessing from the directory name
    JAVA_HOME := $(shell \
      for d in /usr/lib/jvm/*/; do \
        [ -x "$$d/bin/javac" ] || continue; \
        v=$$("$$d/bin/javac" -version 2>&1 | sed -e 's/^javac //' -e 's/[.+_-].*//'); \
        if [ "$$v" -ge $(MIN_JDK) ] 2>/dev/null; then printf '%s\n' "$${d%/}"; fi; \
      done | head -1)
  endif
endif

ifeq ($(strip $(JAVA_HOME)),)
  JAVA_HOME := $(patsubst %/bin/javac,%,$(realpath $(shell command -v javac 2>/dev/null)))
endif

ifeq ($(strip $(JAVA_HOME)),)
  $(error No JDK $(MIN_JDK)+ found. Install one (macOS: brew install --cask temurin@$(MIN_JDK), \
Debian/Ubuntu: apt install openjdk-$(MIN_JDK)-jdk, Fedora: dnf install java-$(MIN_JDK)-openjdk-devel) \
or set JAVA_HOME yourself)
endif

JDK_MAJOR := $(call jdk_major,$(JAVA_HOME))
ifneq ($(call jdk_ok,$(JAVA_HOME)),ok)
  $(error Found JDK "$(JDK_MAJOR)" at $(JAVA_HOME), but $(MIN_JDK)+ is required. \
Set JAVA_HOME to a newer JDK)
endif

export JAVA_HOME

# so gradlew and anything it spawns pick up the same java
PATH := $(JAVA_HOME)/bin:$(PATH)
export PATH

endif

.PHONY: help all debug release clean check install install-debug install-release java-info

# Lists every target carrying a `## description` comment. Deliberately plain POSIX awk:
# no lazy quantifiers (BSD and GNU disagree) and no sed \t (BSD sed does not expand it).
help: ## Show this help
	@echo 'Targets:'
	@awk 'BEGIN {FS = ":.*?## "} { \
		if (/^[0-9a-zA-Z_-]+:.*?##.*$$/) {printf "    \033[1;37m%-20s\033[1;32m%s\033[0m\n", $$1, $$2} \
		else if (/^## .*$$/) {printf "  \033[1;36m%s\033[0m\n", substr($$1,4)} \
		}' $(MAKEFILE_LIST)
		@echo ""
	@echo "  FLAVOR=core|foss|gplay selects the product flavor (currently $(FLAVOR))."
	@echo "  Example: make FLAVOR=core install-debug"

all: release install-release ## release + install-release

debug: ## Assemble the debug APK
	@$(GRADLE) assemble$(FLAVOR)Debug

release: ## Assemble the release APK
	@$(GRADLE) assemble$(FLAVOR)Release

install: install-debug ## Alias for install-debug

# install<Variant> already depends on assemble<Variant>, no need to chain it ourselves
install-debug: ## Build and install the debug APK on the connected device
	@$(GRADLE) install$(FLAVOR)Debug

install-release: ## Build and install the release APK on the connected device
	@$(GRADLE) install$(FLAVOR)Release

clean: ## Delete build outputs
	@$(GRADLE) clean

# Static analysis. The project has no formatter configured (no spotless/ktlint plugin),
# only detekt and the Android linter.
check: ## Run detekt and the Android linter
	@$(GRADLE) detekt lint$(FLAVOR)Debug

# handy when a build picks up an unexpected toolchain
java-info: ## Print the detected JDK and flavor
	@echo "JAVA_HOME = $(JAVA_HOME)"
	@echo "JDK major = $(JDK_MAJOR)"
	@echo "flavor    = $(FLAVOR)"
