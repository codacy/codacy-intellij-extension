package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.cli.impl.MacOsCliImpl
import com.intellij.openapi.components.Service

@Service
class MacOsCli : MacOsCliImpl()

