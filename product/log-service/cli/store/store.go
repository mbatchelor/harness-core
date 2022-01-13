// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package store

import (
	"context"

	"gopkg.in/alecthomas/kingpin.v2"
)

var nocontext = context.Background()

// Register the store commands.
func Register(app *kingpin.Application) {
	cmd := app.Command("store", "storage commands")
	registerDownload(cmd)
	registerDownloadLink(cmd)
	registerUpload(cmd)
	registerUploadLink(cmd)
}
