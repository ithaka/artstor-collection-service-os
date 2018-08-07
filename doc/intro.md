# Introduction to artstor-collection-service-os

Artstor supports hosting collection of images by Public, Institutional, private and personal.
Artstor collection service provides CRUD methods for dealing with collection of images/objects.
Currently this service supports Personal collection only.

Requirements:
1. Hosting software such as Jstor's Forum that can upload to AIW platform is required to publish images to Artstor Workspace.

2. Amazon s3 for storing configuration file.

3. Authentication layer provided Ithaka to complete uploading to AIW.

4. Understanding of Forum's cataloging environment.

5. Postgres to support CRUD operations on public and institutional collections

Library Dependencies:-
1. clj and ring logger libraries
2. Ithaka platform libraries needed for logging, authentication and deployment.
3. Sql libraries yesql and postgresql to support all types of collections in future.
4. ragtime for setting up test database.
5. web api and swagger done using compojure.