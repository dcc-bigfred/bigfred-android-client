# BigFred Android Client
#
#   make apk     — production (release) APK
#   make test    — unit tests (JVM)
#   make debug   — debug APK
#   make clean   — remove build outputs

GRADLE ?= ./gradlew
GRADLE_FLAGS ?= --quiet

APK_RELEASE := app/build/outputs/apk/release/app-release.apk
APK_DEBUG   := app/build/outputs/apk/debug/app-debug.apk

.PHONY: help apk release test test-android debug clean

help:
	@echo "Targets:"
	@echo "  make apk           Build signed release APK → $(APK_RELEASE)"
	@echo "  make release       Alias for apk"
	@echo "  make test          Run JVM unit tests"
	@echo "  make test-android  Run instrumented tests (device/emulator required)"
	@echo "  make debug         Build debug APK → $(APK_DEBUG)"
	@echo "  make clean         Clean Gradle build outputs"
	@echo ""
	@echo "Release signing (optional; falls back to debug keystore):"
	@echo "  BIGFRED_STORE_FILE / BIGFRED_STORE_PASSWORD"
	@echo "  BIGFRED_KEY_ALIAS  / BIGFRED_KEY_PASSWORD"

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
