# BigFred Android Client
#
#   make apk     — production (release) APK
#   make test    — unit tests (JVM)
#   make debug   — debug APK
#   make clean   — remove build outputs
#   make import-models — fetch hydrus.pl catalog into assets/models/
#   make loco-android      — download libloco-server.so (GitHub tip/Release)
#   make valkey-android    — download libvalkey-server.so from deps-android-valkey
#   make microinit-android — download libmicroinit.so (GitHub tip/Release)

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

BIGFRED_REF    ?= main
MICROINIT_REF  ?= main
VALKEY_REPO    ?= dcc-bigfred/deps-android-valkey

CI_SCRIPTS_REPO ?= https://github.com/dcc-bigfred/.github.git
CI_SCRIPTS_REF  ?= v2
CI_SCRIPTS_DIR  ?= .ci-github

.PHONY: help apk release test test-android debug clean import-models \
	loco-android valkey-android microinit-android native-prebuilt \
	ci-scripts ci-scripts-update

help:
	@echo "Targets:"
	@echo "  make apk                 Build signed release APK → $(APK_RELEASE)"
	@echo "  make loco-android        Fetch $(LOCO_SO) (local bin or GitHub $(BIGFRED_REF))"
	@echo "  make valkey-android      Fetch $(VALKEY_SO) from $(VALKEY_REPO) latest release"
	@echo "  make microinit-android   Fetch $(MICROINIT_SO) from GitHub $(MICROINIT_REF)"
	@echo "  make ci-scripts          Clone dcc-bigfred/.github @ $(CI_SCRIPTS_REF)"
	@echo ""
	@echo "Tip refs need GITHUB_TOKEN / GH_TOKEN / BIGFRED_NATIVE_TOKEN"

import-models:
	$(PYTHON) "$(IMPORT_SCRIPT)" --out "$(IMPORT_OUT)"
	mkdir -p "$(ASSETS_MODELS)/images"
	cp "$(IMPORT_OUT)/models.db" "$(ASSETS_MODELS)/models.db"
	rm -rf "$(ASSETS_MODELS)/images"
	cp -a "$(IMPORT_OUT)/images" "$(ASSETS_MODELS)/images"
	@echo "Assets ready: $(ASSETS_MODELS)"

$(CI_SCRIPTS_DIR)/.ok:
	@echo "Cloning $(CI_SCRIPTS_REPO) @ $(CI_SCRIPTS_REF) → $(CI_SCRIPTS_DIR)"
	@rm -rf "$(CI_SCRIPTS_DIR)"
	@git clone --depth 1 --branch "$(CI_SCRIPTS_REF)" "$(CI_SCRIPTS_REPO)" "$(CI_SCRIPTS_DIR)" \
		|| { echo "error: failed to clone $(CI_SCRIPTS_REPO) @ $(CI_SCRIPTS_REF)"; exit 1; }
	@touch "$@"

ci-scripts: $(CI_SCRIPTS_DIR)/.ok

ci-scripts-update:
	rm -rf "$(CI_SCRIPTS_DIR)"
	$(MAKE) "$(CI_SCRIPTS_DIR)/.ok"

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
$(LOCO_SO): $(CI_SCRIPTS_DIR)/.ok
	@mkdir -p "$(NATIVE_PREBUILT)"
	@tmpdir="$$(mktemp -d)"; \
	trap 'rm -rf "$$tmpdir"' EXIT; \
	GITHUB_REPO=dcc-bigfred/bigfred \
	ARTIFACT_NAME=binaries \
	FILES=loco-server-android-arm64:bin/libloco-server.so \
		"$(CI_SCRIPTS_DIR)/scripts/fetch-github-binaries.sh" "$(BIGFRED_REF)" "$$tmpdir/out.tar"; \
	tar -xOf "$$tmpdir/out.tar" bin/libloco-server.so > "$@"
	@chmod 755 "$@"
	@echo "Wrote $@"
endif

valkey-android: $(VALKEY_SO)

$(VALKEY_SO):
	./scripts/fetch-github-release-asset.sh "$(VALKEY_REPO)" libvalkey-server.so "$@"

microinit-android: $(MICROINIT_SO)

$(MICROINIT_SO): $(CI_SCRIPTS_DIR)/.ok
	@mkdir -p "$(NATIVE_PREBUILT)"
	@tmpdir="$$(mktemp -d)"; \
	trap 'rm -rf "$$tmpdir"' EXIT; \
	GITHUB_REPO=dcc-bigfred/microinit \
	ARTIFACT_NAME=binaries-android-arm64 \
	FILES=libmicroinit.so:bin/libmicroinit.so \
		"$(CI_SCRIPTS_DIR)/scripts/fetch-github-binaries.sh" "$(MICROINIT_REF)" "$$tmpdir/out.tar"; \
	tar -xOf "$$tmpdir/out.tar" bin/libmicroinit.so > "$@"
	@chmod 755 "$@"
	@echo "Wrote $@"

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
