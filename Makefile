# BigFred Android Client
#
#   make apk     — production (release) APK
#   make test    — unit tests (JVM)
#   make debug   — debug APK
#   make clean   — remove build outputs
#   make import-models — fetch hydrus.pl catalog into assets/models/
#   make loco-android      — download libloco-server.so from GHCR (ORAS)
#   make valkey-android    — download libvalkey-server.so from deps-android-valkey latest release
#   make microinit-android — download libmicroinit.so from GHCR (ORAS)

GRADLE ?= ./gradlew
GRADLE_FLAGS ?= --quiet
PYTHON ?= python3

APK_RELEASE := app/build/outputs/apk/release/app-release.apk
APK_DEBUG   := app/build/outputs/apk/debug/app-debug.apk

IMPORT_SCRIPT := tools/hydrus-import/import_models.py
IMPORT_OUT := tools/hydrus-import/out
ASSETS_MODELS := app/src/main/assets/models

NATIVE_PREBUILT := native-prebuilt/arm64-v8a
LOCO_SO         := $(NATIVE_PREBUILT)/libloco-server.so
LOCAL_LOCO_BIN  := ../bigfred/bin/loco-server-android-arm64
VALKEY_SO       := $(NATIVE_PREBUILT)/libvalkey-server.so
MICROINIT_SO    := $(NATIVE_PREBUILT)/libmicroinit.so

BIGFRED_OCI_IMAGE ?= ghcr.io/dcc-bigfred/loco-server-android-arm64
BIGFRED_OCI_TAG   ?= main
MICROINIT_OCI_IMAGE ?= ghcr.io/dcc-bigfred/microinit-android-arm64
MICROINIT_OCI_TAG   ?= main

VALKEY_REPO      ?= dcc-bigfred/deps-android-valkey

.PHONY: help apk release test test-android debug clean import-models \
	loco-android valkey-android microinit-android native-prebuilt

help:
	@echo "Targets:"
	@echo "  make apk                 Build signed release APK → $(APK_RELEASE)"
	@echo "  make release             Alias for apk"
	@echo "  make test                Run JVM unit tests"
	@echo "  make test-android        Run instrumented tests (device/emulator required)"
	@echo "  make debug               Build debug APK → $(APK_DEBUG)"
	@echo "  make import-models       Import hydrus models DB + thumbs → $(ASSETS_MODELS)"
	@echo "  make loco-android        Fetch $(LOCO_SO) (local $(LOCAL_LOCO_BIN) if present, else $(BIGFRED_OCI_IMAGE):$(BIGFRED_OCI_TAG); FORCE=1)"
	@echo "  make valkey-android      Fetch $(VALKEY_SO) from $(VALKEY_REPO) latest release (skip if exists; FORCE=1)"
	@echo "  make microinit-android   Fetch $(MICROINIT_SO) from $(MICROINIT_OCI_IMAGE):$(MICROINIT_OCI_TAG) (skip if exists; FORCE=1)"
	@echo "  make clean               Clean Gradle build outputs"
	@echo ""
	@echo "Release signing (optional; falls back to debug keystore):"
	@echo "  BIGFRED_STORE_FILE / BIGFRED_STORE_PASSWORD"
	@echo "  BIGFRED_KEY_ALIAS  / BIGFRED_KEY_PASSWORD"
	@echo ""
	@echo "Private GitHub deps (optional): GITHUB_TOKEN / GH_TOKEN / BIGFRED_NATIVE_TOKEN"

import-models:
	$(PYTHON) "$(IMPORT_SCRIPT)" --out "$(IMPORT_OUT)"
	mkdir -p "$(ASSETS_MODELS)/images"
	cp "$(IMPORT_OUT)/models.db" "$(ASSETS_MODELS)/models.db"
	rm -rf "$(ASSETS_MODELS)/images"
	cp -a "$(IMPORT_OUT)/images" "$(ASSETS_MODELS)/images"
	@echo "Assets ready: $(ASSETS_MODELS)"
	@ls -lh "$(ASSETS_MODELS)/models.db"
	@echo "Images: $$(find "$(ASSETS_MODELS)/images" -type f | wc -l)"

# --- Native prebuilts (download from deps-* GitHub Releases) -----------------
# Thin Make rules: skip when the output exists. FORCE=1 removes first.

ifdef FORCE
.PHONY: force-clean-native
force-clean-native:
	rm -f "$(LOCO_SO)" "$(VALKEY_SO)" "$(MICROINIT_SO)"
loco-android: force-clean-native
valkey-android: force-clean-native
microinit-android: force-clean-native
endif

loco-android: $(LOCO_SO)

ifneq ($(wildcard $(LOCAL_LOCO_BIN)),)
$(LOCO_SO): $(LOCAL_LOCO_BIN)
	@mkdir -p "$(NATIVE_PREBUILT)"
	@cp "$<" "$@"
	@echo "Using local $< → $@"
else
$(LOCO_SO):
	@mkdir -p "$(NATIVE_PREBUILT)"
	./scripts/fetch-ghcr-oras.sh "$(BIGFRED_OCI_IMAGE)" "$(BIGFRED_OCI_TAG)" "$@" main
endif

valkey-android: $(VALKEY_SO)

$(VALKEY_SO):
	./scripts/fetch-github-release-asset.sh "$(VALKEY_REPO)" libvalkey-server.so "$@"

microinit-android: $(MICROINIT_SO)

$(MICROINIT_SO):
	@mkdir -p "$(NATIVE_PREBUILT)"
	./scripts/fetch-ghcr-oras.sh "$(MICROINIT_OCI_IMAGE)" "$(MICROINIT_OCI_TAG)" "$@" main

native-prebuilt: loco-android valkey-android microinit-android

apk release: native-prebuilt
	$(GRADLE) $(GRADLE_FLAGS) :app:assembleRelease
	@echo "APK: $(APK_RELEASE)"
	@ls -lh "$(APK_RELEASE)"

debug: native-prebuilt
	$(GRADLE) $(GRADLE_FLAGS) :app:assembleDebug
	@echo "APK: $(APK_DEBUG)"
	@ls -lh "$(APK_DEBUG)"

test:
	$(GRADLE) $(GRADLE_FLAGS) :app:testDebugUnitTest

test-android:
	$(GRADLE) $(GRADLE_FLAGS) :app:connectedDebugAndroidTest

clean:
	$(GRADLE) $(GRADLE_FLAGS) clean
