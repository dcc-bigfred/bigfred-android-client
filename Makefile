# BigFred Android Client
#
#   make apk     — production (release) APK
#   make test    — unit tests (JVM)
#   make debug   — debug APK
#   make clean   — remove build outputs
#   make import-models — fetch hydrus.pl catalog into assets/models/

GRADLE ?= ./gradlew
GRADLE_FLAGS ?= --quiet
PYTHON ?= python3

APK_RELEASE := app/build/outputs/apk/release/app-release.apk
APK_DEBUG   := app/build/outputs/apk/debug/app-debug.apk

IMPORT_SCRIPT := tools/hydrus-import/import_models.py
IMPORT_OUT := tools/hydrus-import/out
ASSETS_MODELS := app/src/main/assets/models

.PHONY: help apk release test test-android debug clean import-models

help:
	@echo "Targets:"
	@echo "  make apk           Build signed release APK → $(APK_RELEASE)"
	@echo "  make release       Alias for apk"
	@echo "  make test          Run JVM unit tests"
	@echo "  make test-android  Run instrumented tests (device/emulator required)"
	@echo "  make debug         Build debug APK → $(APK_DEBUG)"
	@echo "  make import-models Import hydrus models DB + thumbs → $(ASSETS_MODELS)"
	@echo "  make clean         Clean Gradle build outputs"
	@echo ""
	@echo "Release signing (optional; falls back to debug keystore):"
	@echo "  BIGFRED_STORE_FILE / BIGFRED_STORE_PASSWORD"
	@echo "  BIGFRED_KEY_ALIAS  / BIGFRED_KEY_PASSWORD"

import-models:
	$(PYTHON) "$(IMPORT_SCRIPT)" --out "$(IMPORT_OUT)"
	mkdir -p "$(ASSETS_MODELS)/images"
	cp "$(IMPORT_OUT)/models.db" "$(ASSETS_MODELS)/models.db"
	rm -rf "$(ASSETS_MODELS)/images"
	cp -a "$(IMPORT_OUT)/images" "$(ASSETS_MODELS)/images"
	@echo "Assets ready: $(ASSETS_MODELS)"
	@ls -lh "$(ASSETS_MODELS)/models.db"
	@echo "Images: $$(find "$(ASSETS_MODELS)/images" -type f | wc -l)"

apk release:
	$(GRADLE) $(GRADLE_FLAGS) :app:assembleRelease
	@echo "APK: $(APK_RELEASE)"
	@ls -lh "$(APK_RELEASE)"

debug:
	$(GRADLE) $(GRADLE_FLAGS) :app:assembleDebug
	@echo "APK: $(APK_DEBUG)"
	@ls -lh "$(APK_DEBUG)"

test:
	$(GRADLE) $(GRADLE_FLAGS) :app:testDebugUnitTest

test-android:
	$(GRADLE) $(GRADLE_FLAGS) :app:connectedDebugAndroidTest

clean:
	$(GRADLE) $(GRADLE_FLAGS) clean
