# Introduction to artstor-collection-service-os

Artstor collection service provides CRUD methods for dealing with collection of images/objects.

Personal collection : Services for handling personal collection of images at AIW

Delete a personal collection Image          DELETE /api/v1/pcollection/image

Upload a personal collection Image          POST /api/v1/pcollection/image

Update a personal collection Media image    PUT /api/v1/pcollection/image/{ssid}

Update a personal collection Images metadata
                            POST /api/v1/pcollection/image/metadata

Read personal collection Images metadata field definitions
                            GET /api/v1/pcollection/image/metadata/definitions

Personal collection Image Pulbishing Status by ssid
                            GET /api/v1/pcollection/image-status/{ssid}


Artstor contributed content category description
                    /api/v1/categorydesc/{id}  Get Category Description