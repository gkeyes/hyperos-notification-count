#!/usr/bin/env python3
"""Read-only DEX ABI and modern Xposed APK packaging checks (Python stdlib only)."""

import argparse
import hashlib
import json
import re
import shutil
import struct
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path


MODULE_ROOT = Path(__file__).resolve().parents[1]
BAR = "com.android.systemui.statusbar."
COLLECTION = BAR + "notification.collection."
BINDER = BAR + "pipeline.shared.ui.binder."
PIPELINE = COLLECTION + "NotifPipeline"
ENTRY = COLLECTION + "NotificationEntry"
USERS = BAR + "NotificationLockscreenUserManagerImpl"
COORDINATOR = COLLECTION + "coordinator.HideNotifsForOtherUsersCoordinator"
CONTAINER = BAR + "phone.NotificationIconContainer"
INJECT = BAR + "phone.NotificationIconContainerInject"
MONITOR = BAR + "IslandMonitor$NotificationContainerIslandMonitor"
DISPATCHER = "com.android.systemui.plugins.DarkIconDispatcher"
RECEIVER = DISPATCHER + "$DarkReceiver"
USER_LISTENER = BAR + "NotificationLockscreenUserManager$UserChangedListener"
COLLECTION_LISTENER = COLLECTION + "notifcollection.NotifCollectionListener"
RENDER_LISTENER = COLLECTION + "listbuilder.OnBeforeRenderListListener"
MODULE_ENTRY = "dev.hyperos.notificationcount.NotificationCountModule"
NOTIFICATION = BAR + "notification."
WRAPPER = NOTIFICATION + "ExpandedNotification"
PIPELINE_ENTRY = COLLECTION + "PipelineEntry"
ATTACH_STATE = COLLECTION + "ListAttachState"
SECTION = COLLECTION + "listbuilder.NotifSection"
SECTION_STYLE = COLLECTION + "provider.SectionStyleProvider"
PRIORITY = COLLECTION + "provider.HighPriorityProvider"
FOCUS_UTILS = NOTIFICATION + "utils.FocusUtils"
NOTIF_UTIL = NOTIFICATION + "utils.NotificationUtil"
HEADS_UP = NOTIFICATION + "headsup.HeadsUpManagerImpl"
PINNED = NOTIFICATION + "headsup.PinnedStatus"
RENDERED = NOTIFICATION + "domain.interactor.RenderNotificationListInteractor"


def ref(name):
    return "L" + name.replace(".", "/") + ";"


# Small declaration manifest: no private APK or decompiled implementation is needed in CI.
EXPECTED_TYPES = {
    PIPELINE, COLLECTION + "NotifCollection", COORDINATOR, ENTRY, ENTRY + "$DismissState",
    USERS, USER_LISTENER, COLLECTION_LISTENER, RENDER_LISTENER,
    BINDER + "HomeStatusBarViewBinderImpl", BINDER + "HomeStatusBarViewBinderInjector",
    BAR + "phone.PhoneStatusBarView", BAR + "pipeline.shared.ui.viewmodel.HomeStatusBarViewModel",
    CONTAINER, BAR + "StatusBarIconView", MONITOR, INJECT, DISPATCHER, RECEIVER,
    "kotlin.jvm.functions.Function1",
    WRAPPER, PIPELINE_ENTRY, ATTACH_STATE, SECTION, SECTION_STYLE, PRIORITY,
    FOCUS_UTILS, NOTIF_UTIL, HEADS_UP, HEADS_UP + "$HeadsUpEntry", PINNED, RENDERED,
}
EXPECTED_FIELDS = {
    (PIPELINE, "mNotifCollection"): ref(COLLECTION + "NotifCollection"),
    (COORDINATOR, "mLockscreenUserManager"): ref(BAR + "NotificationLockscreenUserManager"),
    (USERS, "mListeners"): "Ljava/util/List;",
    (ENTRY, "mSbn"): ref(BAR + "notification.ExpandedNotification"),
    (ENTRY, "mDismissState"): ref(ENTRY + "$DismissState"),
    (ENTRY, "mCancellationReason"): "I",
    (ENTRY + "$DismissState", "NOT_DISMISSED"): ref(ENTRY + "$DismissState"),
    (BINDER + "HomeStatusBarViewBinderImpl", "mInjector"): ref(BINDER + "HomeStatusBarViewBinderInjector"),
    (MONITOR, "container"): ref(CONTAINER),
    (BINDER + "HomeStatusBarViewBinderInjector", "mNotificationIconAreaInner"): "Landroid/view/View;",
    (BINDER + "HomeStatusBarViewBinderInjector", "darkIconDispatcher"): ref(DISPATCHER),
    (CONTAINER, "mInject"): ref(INJECT),
    (INJECT, "showNotificationIcons"): "I",
    (INJECT, "_islandMonitor"): ref(MONITOR),
    (MONITOR, "islandWidth"): "I",
    (WRAPPER, "mIsFocusNotification"): "Z",
    (WRAPPER, "mIsPromotedOngoing"): "Z",
    (WRAPPER, "mIsFold"): "Z",
    (PIPELINE_ENTRY, "attachState"): ref(ATTACH_STATE),
    (ATTACH_STATE, "section"): ref(SECTION),
    (SECTION, "bucket"): "I",
    (SECTION, "sectioner"): ref(COLLECTION + "listbuilder.pluggable.NotifSectioner"),
    (SECTION_STYLE, "silentSections"): "Ljava/util/Set;",
    (SECTION_STYLE, "highPriorityProvider"): ref(PRIORITY),
    (RENDERED, "sectionStyleProvider"): ref(SECTION_STYLE),
}
EXPECTED_METHODS = {
    (PIPELINE, "addCollectionListener", (ref(COLLECTION_LISTENER),)): "V",
    (PIPELINE, "addOnBeforeRenderListListener", (ref(RENDER_LISTENER),)): "V",
    (USERS, "isCurrentProfile", ("I",)): "Z",
    (PIPELINE, "getAllNotifs", ()): "Ljava/util/Collection;",
    (COORDINATOR, "attach", (ref(PIPELINE),)): "V",
    (COLLECTION + "NotifCollection", "dispatchEventsAndRebuildList", ("Ljava/lang/String;",)): "V",
    (COLLECTION + "NotifCollection", "dismissNotifications", ("Ljava/util/List;", "Z")): "V",
    (COLLECTION + "NotifCollection", "dismissAllNotifications", ("I",)): "V",
    (BINDER + "HomeStatusBarViewBinderImpl", "bind", (
        ref(BAR + "phone.PhoneStatusBarView"),
        ref(BAR + "pipeline.shared.ui.viewmodel.HomeStatusBarViewModel"),
        "Lkotlin/jvm/functions/Function1;", "Lkotlin/jvm/functions/Function1;")): "V",
    (BINDER + "HomeStatusBarViewBinderInjector", "onUnbind", ()): "V",
    (CONTAINER, "onMeasure", ("I", "I")): "V",
    (CONTAINER, "onLayout", ("Z", "I", "I", "I", "I")): "V",
    (CONTAINER, "onConfigurationChanged", ("Landroid/content/res/Configuration;",)): "V",
    (CONTAINER, "setMaxIconsAmount", ("I",)): "V",
    (BAR + "StatusBarIconView", "onDraw", ("Landroid/graphics/Canvas;",)): "V",
    (MONITOR, "updateContainerSize", ("Landroid/graphics/Rect;", "Z", "Z")): "V",
    (DISPATCHER, "addDarkReceiver", (ref(RECEIVER),)): "V",
    (DISPATCHER, "removeDarkReceiver", (ref(RECEIVER),)): "V",
    (DISPATCHER, "getTint", ("Ljava/util/Collection;", "Landroid/view/View;", "I")): "I",
    (CONTAINER, "getActualPaddingStart", ()): "F",
    (CONTAINER, "getActualPaddingEnd", ()): "F",
    (FOCUS_UTILS, "isUpdatableFocusNotification", ("Landroid/app/Notification;",)): "Z",
    (WRAPPER, "isPersistent", ()): "Z",
    (ENTRY, "isClearable", ()): "Z",
    (ENTRY, "isRowPinned", ()): "Z",
    (NOTIF_UTIL, "isMiuiMediaNotification", (ref(ENTRY),)): "Z",
    (PRIORITY, "isHighPriorityConversation", (ref(PIPELINE_ENTRY),)): "Z",
    (NOTIF_UTIL, "setFold", (ref(ENTRY), "Z")): "V",
    (HEADS_UP, "setEntryPinned", (ref(HEADS_UP + "$HeadsUpEntry"), ref(PINNED), "Ljava/lang/String;")): "V",
    (RENDERED, "setRenderedList", ("Ljava/util/List;",)): "V",
}
SDK_METHOD = ("android.view.View", "setMeasuredDimension", ("I", "I"))
HOOK_METHODS = {
    key for key in EXPECTED_METHODS
    if key[1] in {"attach", "dispatchEventsAndRebuildList", "bind", "onUnbind", "onMeasure",
                  "onLayout", "onConfigurationChanged", "setMaxIconsAmount", "onDraw", "updateContainerSize",
                  "dismissNotifications", "dismissAllNotifications", "setFold", "setEntryPinned", "setRenderedList"}
}
DEOPT_METHODS = {key for key in EXPECTED_METHODS
                 if key[1] in {"dismissNotifications", "dismissAllNotifications"}}

# Framework-facing ABI, verified against the published api:102.0.0 sources.
# java_init.list names the entry; these virtual callbacks use the external API descriptors.
MODULE_ARTIFACT_METHODS = {
    (MODULE_ENTRY, "<init>", ()): "V",
    (MODULE_ENTRY, "onModuleLoaded", (
        "Lio/github/libxposed/api/XposedModuleInterface$ModuleLoadedParam;",)): "V",
    (MODULE_ENTRY, "onPackageReady", (
        "Lio/github/libxposed/api/XposedModuleInterface$PackageReadyParam;",)): "V",
    ("dev.hyperos.notificationcount.hook.AfterHook", "intercept", (
        "Lio/github/libxposed/api/XposedInterface$Chain;",)): "Ljava/lang/Object;",
    ("dev.hyperos.notificationcount.hook.ClippedDrawHook", "intercept", (
        "Lio/github/libxposed/api/XposedInterface$Chain;",)): "Ljava/lang/Object;",
}


def require(condition, message):
    # Do not use Python's assert statement: -O must not disable a packaging gate.
    if not condition:
        raise AssertionError(message)


@dataclass(frozen=True)
class Method:
    name: str
    parameters: tuple
    returns: str
    flags: int
    code_offset: int


class Dex:
    def __init__(self, name, data):
        require(data[:4] == b"dex\n" and len(data) >= 112, f"{name}: unsupported DEX header")
        require(self.u32(data, 0x28) == 0x12345678, f"{name}: unsupported byte order")
        self.name, self.data = name, data
        self.strings_offset = self.u32(data, 0x3C)
        self.types_offset = self.u32(data, 0x44)
        self.protos_offset = self.u32(data, 0x4C)
        self.fields_offset = self.u32(data, 0x54)
        self.methods_offset = self.u32(data, 0x5C)
        self.classes = {}
        for index in range(self.u32(data, 0x60)):
            offset = self.u32(data, 0x64) + index * 32
            descriptor = self.type(self.u32(data, offset))
            parent = self.u32(data, offset + 8)
            self.classes[descriptor] = {
                "flags": self.u32(data, offset + 4),
                "parent": None if parent == 0xFFFFFFFF else self.type(parent),
                "class_data": self.u32(data, offset + 24),
            }

    @staticmethod
    def u32(data, offset):
        return struct.unpack_from("<I", data, offset)[0]

    def uleb(self, offset):
        value = 0
        for shift in range(0, 35, 7):
            byte = self.data[offset]
            offset += 1
            value |= (byte & 0x7F) << shift
            if byte < 0x80:
                return value, offset
        raise AssertionError(f"{self.name}: invalid ULEB128")

    @lru_cache(None)
    def string(self, index):
        offset = self.u32(self.data, self.strings_offset + index * 4)
        _, offset = self.uleb(offset)
        # The declaration names/descriptors used here are ASCII; no MUTF-8 text is compared.
        return self.data[offset:self.data.index(0, offset)].decode("utf-8", errors="replace")

    def type(self, index):
        return self.string(self.u32(self.data, self.types_offset + index * 4))

    @lru_cache(None)
    def members(self, descriptor):
        offset = self.classes[descriptor]["class_data"]
        fields, methods = {}, []
        if not offset:
            return fields, methods
        counts = []
        for _ in range(4):
            count, offset = self.uleb(offset)
            counts.append(count)
        for count in counts[:2]:
            index = 0
            for _ in range(count):
                delta, offset = self.uleb(offset)
                index += delta
                flags, offset = self.uleb(offset)
                owner, field_type, name = struct.unpack_from("<HHI", self.data, self.fields_offset + index * 8)
                require(self.type(owner) == descriptor, f"{self.name}: mismatched field owner")
                fields[self.string(name)] = (self.type(field_type), flags)
        for count in counts[2:]:
            index = 0
            for _ in range(count):
                delta, offset = self.uleb(offset)
                index += delta
                flags, offset = self.uleb(offset)
                code, offset = self.uleb(offset)
                owner, proto, name = struct.unpack_from("<HHI", self.data, self.methods_offset + index * 8)
                require(self.type(owner) == descriptor, f"{self.name}: mismatched method owner")
                proto_offset = self.protos_offset + proto * 12
                args_offset = self.u32(self.data, proto_offset + 8)
                parameters = ()
                if args_offset:
                    parameters = tuple(self.type(struct.unpack_from(
                        "<H", self.data, args_offset + 4 + item * 2)[0])
                        for item in range(self.u32(self.data, args_offset)))
                methods.append(Method(self.string(name), parameters,
                                      self.type(self.u32(self.data, proto_offset + 4)), flags, code))
        return fields, methods


class ApkDex:
    def __init__(self, archive):
        self.classes = {}
        self.dex_names = sorted(name for name in archive.namelist()
                                if re.fullmatch(r"classes(?:[2-9][0-9]*|1[0-9]+)?\.dex", name))
        require(self.dex_names, "APK has no classes.dex definitions")
        for name in self.dex_names:
            dex = Dex(name, archive.read(name))
            for descriptor in dex.classes:
                require(descriptor not in self.classes, f"Duplicate DEX class definition: {descriptor}")
                self.classes[descriptor] = dex

    def field(self, descriptor, name):
        visited = set()
        while descriptor in self.classes and descriptor not in visited:
            visited.add(descriptor)
            dex = self.classes[descriptor]
            fields, _ = dex.members(descriptor)
            if name in fields:
                return descriptor, dex.name, fields[name]
            descriptor = dex.classes[descriptor]["parent"]
        raise AssertionError(f"Missing declared/inherited host field: {descriptor}.{name}")

    def method(self, owner, name, parameters):
        descriptor = ref(owner)
        require(descriptor in self.classes, f"Missing host class: {owner}")
        dex = self.classes[descriptor]
        _, methods = dex.members(descriptor)
        matches = [method for method in methods if method.name == name and method.parameters == parameters]
        require(len(matches) == 1, f"Missing/ambiguous declared method: {owner}.{name}{parameters}")
        return dex.name, matches[0]


def split_expression(expression, separator=","):
    parts, start, depth, quoted, escaped = [], 0, 0, False, False
    for index, character in enumerate(expression):
        if quoted:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                quoted = False
        elif character == '"':
            quoted = True
        elif character in "([":
            depth += 1
        elif character in ")]":
            depth -= 1
        elif character == separator and depth == 0:
            parts.append(expression[start:index].strip())
            start = index + 1
    parts.append(expression[start:].strip())
    return parts


def source_targets():
    types, fields, methods, hooks, deopts = set(), set(), set(), set(), set()
    primitive = {"int": "I", "boolean": "Z", "float": "F", "long": "J", "double": "D",
                 "byte": "B", "char": "C", "short": "S", "void": "V"}
    for filename in ("SystemUiHooks.java", "StatusBarRenderer.java", "HostAccess.java", "NotificationClassifier.java"):
        path = MODULE_ROOT / "app/src/main/java/dev/hyperos/notificationcount/hook" / filename
        text = path.read_text()
        text = re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/',
                      lambda match: match[0] if match[0].startswith('"')
                      else re.sub(r"[^\n]", " ", match[0]), text, flags=re.S)
        strings, classes, method_variables = {}, {}, {}
        imports = {name.rsplit(".", 1)[-1]: name for name in
                   re.findall(r"import\s+([\w.$]+)\s*;", text)}
        imports.update({"String": "java.lang.String", "Object": "java.lang.Object"})

        def string_expression(expression):
            result = ""
            for item in split_expression(expression, "+"):
                if item.startswith('"'):
                    result += json.loads(item)
                else:
                    require(item in strings, f"{filename}: unsupported string expression: {item}")
                    result += strings[item]
            return result

        def class_expression(expression):
            expression = expression.strip()
            if expression.startswith("access.type(") and expression.endswith(")"):
                return string_expression(expression[len("access.type("):-1])
            if expression.endswith(".class"):
                name = expression[:-6]
                return imports.get(name, name)
            require(expression in classes, f"{filename}: unresolved class expression: {expression}")
            return classes[expression]

        for match in re.finditer(r"(?:private\s+)?static\s+final\s+String\s+(\w+)\s*=\s*([^;]+);", text):
            strings[match[1]] = string_expression(match[2])

        for match in re.finditer(r"(access\.type|HostAccess\.(?:field|method))\s*\(", text):
            start, index, depth, quoted, escaped = match.end(), match.end(), 1, False, False
            while index < len(text) and depth:
                char = text[index]
                if quoted:
                    if escaped: escaped = False
                    elif char == "\\": escaped = True
                    elif char == '"': quoted = False
                elif char == '"': quoted = True
                elif char == "(": depth += 1
                elif char == ")": depth -= 1
                index += 1
            require(depth == 0, f"{filename}: unterminated HostAccess call")
            arguments = split_expression(text[start:index - 1])
            assignment = re.search(r"(\w+)\s*=\s*$", text[max(0, match.start() - 100):match.start()])
            kind = match[1]
            if kind == "access.type":
                owner = string_expression(arguments[0])
                types.add(owner)
                if assignment: classes[assignment[1]] = owner
            elif kind == "HostAccess.field":
                fields.add((class_expression(arguments[0]), string_expression(arguments[1])))
            else:
                arguments_types = tuple(primitive.get(name, ref(name))
                                        for name in map(class_expression, arguments[2:]))
                key = (class_expression(arguments[0]), string_expression(arguments[1]), arguments_types)
                methods.add(key)
                if assignment: method_variables[assignment[1]] = key
        for match in re.finditer(r"(?:after|module\.hook)\s*\(\s*(\w+)\s*[,)]", text):
            variable = match[1]
            if variable == "method":
                continue  # The shared after(Method method, ...) helper, not a new target.
            require(variable in method_variables, f"{filename}: unresolved hook executable: {variable}")
            hooks.add(method_variables[variable])
        for match in re.finditer(r"module\.deoptimize\s*\(\s*(\w+)\s*\)", text):
            variable = match[1]
            require(variable in method_variables, f"{filename}: unresolved deoptimization executable: {variable}")
            deopts.add(method_variables[variable])
    require(types == EXPECTED_TYPES, f"HostAccess.type inventory changed: {types ^ EXPECTED_TYPES}")
    require(fields == set(EXPECTED_FIELDS), f"HostAccess.field inventory changed: {fields ^ set(EXPECTED_FIELDS)}")
    require(methods == set(EXPECTED_METHODS) | {SDK_METHOD},
            f"HostAccess.method inventory changed: {methods ^ (set(EXPECTED_METHODS) | {SDK_METHOD})}")
    require(hooks == HOOK_METHODS, f"Hook registration inventory changed: {hooks ^ HOOK_METHODS}")
    require(deopts == DEOPT_METHODS, f"Deoptimization inventory changed: {deopts ^ DEOPT_METHODS}")
    return {"types": len(types), "fields": len(fields), "host_methods": len(EXPECTED_METHODS),
            "sdk_methods": 1, "hook_executables": len(hooks), "deoptimization_executables": len(deopts)}


def digest(path):
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def verify_systemui(path):
    inventory = source_targets()
    result = {"sha256": digest(path), "source_inventory": inventory, "fields": [], "methods": []}
    with zipfile.ZipFile(path) as archive:
        host = ApkDex(archive)
        for owner in sorted(EXPECTED_TYPES):
            require(ref(owner) in host.classes, f"Missing host type definition: {owner}")
        for (owner, name), expected in sorted(EXPECTED_FIELDS.items()):
            declared_owner, dex, (actual, flags) = host.field(ref(owner), name)
            require(actual == expected, f"Field type mismatch: {owner}.{name}: {actual}, expected {expected}")
            if name == "NOT_DISMISSED":
                require(flags & 0x8, "NOT_DISMISSED must be a static field")
            result["fields"].append({"owner": owner, "name": name, "type": actual,
                                     "declared_owner": declared_owner, "dex": dex, "flags": hex(flags)})
        for key, expected in sorted(EXPECTED_METHODS.items()):
            owner, name, parameters = key
            dex, method = host.method(*key)
            require(method.returns == expected, f"Return type mismatch: {owner}.{name}: {method.returns}")
            if key in HOOK_METHODS | DEOPT_METHODS:
                require(not method.flags & 0x400 and method.code_offset != 0,
                        f"Hook method has no concrete DEX body: {owner}.{name}")
            if name == "getTint":
                require(method.flags & 0x8, "DarkIconDispatcher.getTint must be static")
            result["methods"].append({"owner": owner, "name": name,
                                      "descriptor": "(" + "".join(parameters) + ")" + method.returns,
                                      "dex": dex, "flags": hex(method.flags), "hook": key in HOOK_METHODS,
                                      "deoptimize": key in DEOPT_METHODS})
        result["listeners"] = {}
        for owner in (USER_LISTENER, COLLECTION_LISTENER, RENDER_LISTENER, RECEIVER):
            dex = host.classes[ref(owner)]
            require(dex.classes[ref(owner)]["flags"] & 0x200, f"Listener must be an interface: {owner}")
            _, callbacks = dex.members(ref(owner))
            require(callbacks, f"Listener has no declared callbacks: {owner}")
            require(all(method.returns == "V" for method in callbacks),
                    f"Proxy callback has a non-void return: {owner}")
            result["listeners"][owner] = [method.name + "(" + "".join(method.parameters) + ")V"
                                            for method in callbacks]
        _, dark_changed = host.method(RECEIVER, "onDarkChanged", ("Ljava/util/ArrayList;", "F", "I"))
        require(dark_changed.returns == "V", "Unexpected DarkReceiver.onDarkChanged signature")
        descriptor, visited = EXPECTED_FIELDS[(ENTRY, "mSbn")], set()
        target = "Landroid/service/notification/StatusBarNotification;"
        while descriptor in host.classes and descriptor not in visited and descriptor != target:
            visited.add(descriptor)
            dex = host.classes[descriptor]
            descriptor = dex.classes[descriptor]["parent"]
        require(descriptor == target, "NotificationEntry.mSbn does not extend StatusBarNotification")
        result["notification_wrapper_extends_sbn"] = True
    return result


def verify_sdk(path):
    javap = shutil.which("javap")
    require(javap is not None, "--sdk-jar requires javap on PATH")
    process = subprocess.run([javap, "-classpath", str(path), "-p", "android.view.View"],
                             capture_output=True, text=True, timeout=30)
    require(process.returncode == 0, "SDK javap failed: " + process.stderr.strip())
    declaration = "protected final void setMeasuredDimension(int, int);"
    require(declaration in process.stdout, "Missing SDK declaration: " + declaration)
    return {"sha256": digest(path), "declaration": declaration, "evidence": "SDK javap, no compilation"}


def verify_module_apk(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        required = {"AndroidManifest.xml", "META-INF/xposed/java_init.list",
                    "META-INF/xposed/scope.list", "META-INF/xposed/module.prop"}
        for name in required:
            require(names.count(name) == 1, f"Missing/duplicate ZIP entry: {name}")
        def lines(name):
            return [line.strip() for line in archive.read(name).decode("utf-8").splitlines()
                    if line.strip() and not line.lstrip().startswith("#")]
        require(lines("META-INF/xposed/java_init.list") == [MODULE_ENTRY], "Unexpected modern Java entry list")
        require(lines("META-INF/xposed/scope.list") == ["com.android.systemui"], "Scope must be exactly SystemUI")
        properties = {}
        for line in lines("META-INF/xposed/module.prop"):
            require("=" in line, "Malformed module.prop line")
            key, value = (item.strip() for item in line.split("=", 1))
            require(key not in properties, f"Duplicate module.prop key: {key}")
            properties[key] = value
        for key, expected in {"minApiVersion": "102", "targetApiVersion": "102",
                              "staticScope": "true", "autoHotReload": "false"}.items():
            require(properties.get(key) == expected, f"module.prop {key} must be {expected}")
        forbidden = {"assets/xposed_init", "assets/xposed_init.list", "assets/xposed_scope"}
        require(not forbidden.intersection(names), "Legacy Xposed ZIP entry is packaged")
        require(not any(name.startswith(("io/github/libxposed/api/", "de/robv/android/xposed/"))
                        for name in names), "Loose framework/legacy API classes are packaged")
        dex = ApkDex(archive)
        require(ref(MODULE_ENTRY) in dex.classes, "Modern module entry is not a defined DEX class")
        require(not any(name.startswith(("Lio/github/libxposed/api/", "Lde/robv/android/xposed/"))
                        for name in dex.classes), "Framework/legacy Xposed API definitions are packaged")
        entry_dex = dex.classes[ref(MODULE_ENTRY)]
        require(entry_dex.classes[ref(MODULE_ENTRY)]["parent"] == "Lio/github/libxposed/api/XposedModule;",
                "Modern entry must extend XposedModule")
        runtime_classes = sorted({owner for owner, _, _ in MODULE_ARTIFACT_METHODS})
        for owner in runtime_classes:
            descriptor = ref(owner)
            require(descriptor in dex.classes, f"Missing module ABI class: {owner}")
            flags = dex.classes[descriptor].classes[descriptor]["flags"]
            require(flags & 0x1 and not flags & (0x200 | 0x400),
                    f"Module ABI class must be public and concrete: {owner}")
        runtime_methods = []
        for (owner, name, parameters), returns in MODULE_ARTIFACT_METHODS.items():
            dex_name, method = dex.method(owner, name, parameters)
            signature = name + "(" + "".join(parameters) + ")" + returns
            require(method.returns == returns, f"Wrong module ABI return type: {owner}.{signature}")
            require(method.flags & 0x1 and not method.flags & (0x8 | 0x100 | 0x400)
                    and method.code_offset > 0,
                    f"Module ABI method must be a public, concrete instance method: {owner}.{signature}")
            if name == "<init>":
                require(method.flags & 0x10000, f"Module entry lacks a constructor flag: {owner}.{signature}")
            runtime_methods.append({"owner": owner, "declaration": signature, "dex": dex_name,
                                    "access_flags": hex(method.flags), "code_present": True})
    return {"sha256": digest(path), "java_entry": MODULE_ENTRY, "scope": ["com.android.systemui"],
            "module_properties": properties, "legacy_zip_entries_absent": True,
            "framework_api_definitions_absent": True, "module_entry_defined": True,
            "module_runtime_abi": {"public_concrete_classes": runtime_classes,
                                   "public_concrete_instance_methods": runtime_methods},
            "dex_files": dex.dex_names, "dex_class_definitions": len(dex.classes),
            "manifest_components_permissions": "NOT_CHECKED: inspect AndroidManifest.xml with aapt separately",
            "runtime_hook_hits": "NOT_CHECKED"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="Module APK: ZIP/DEX packaging only; no private SystemUI APK required")
    parser.add_argument("--systemui-apk", type=Path, help="Explicit extracted SystemUI APK for source-to-DEX ABI checks")
    parser.add_argument("--sdk-jar", type=Path, help="Optional Android SDK android.jar for javap of View.setMeasuredDimension")
    parser.add_argument("--json", action="store_true", help="Print declaration evidence as JSON")
    arguments = parser.parse_args()
    if not (arguments.apk or arguments.systemui_apk):
        parser.error("Specify --apk and/or --systemui-apk")
    require(not arguments.sdk_jar or arguments.systemui_apk, "--sdk-jar requires --systemui-apk")
    result = {"verification": "STATIC_ONLY", "runtime_hook_hits": "NOT_CHECKED"}
    if arguments.systemui_apk:
        result["systemui_abi"] = verify_systemui(arguments.systemui_apk)
        result["sdk_method"] = verify_sdk(arguments.sdk_jar) if arguments.sdk_jar else {
            "declaration": "android.view.View.setMeasuredDimension(II)V", "evidence": "NOT_CHECKED in this invocation"}
    if arguments.apk:
        result["module_apk"] = verify_module_apk(arguments.apk)
    if arguments.json:
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        if "systemui_abi" in result:
            print("PASS SystemUI DEX ABI:", json.dumps(result["systemui_abi"]["source_inventory"]))
            print("SystemUI SHA-256:", result["systemui_abi"]["sha256"])
            print("SDK method:", result["sdk_method"]["evidence"])
        if "module_apk" in result:
            print("PASS module APK ZIP/DEX metadata and API 102 entry/Hooker ABI:",
                  result["module_apk"]["sha256"])
            print("Manifest components/permissions require separate aapt inspection.")
        print("Static declarations/packaging only; ART hook hits and device behavior are not verified.")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError, ValueError, KeyError, IndexError, struct.error,
            zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
