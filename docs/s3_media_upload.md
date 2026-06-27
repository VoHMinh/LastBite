# S3 media upload

Backend only creates a short-lived S3 pre-signed `PUT` URL. The frontend uploads the file directly to S3, then calls the confirm API so the backend can mark the media as confirmed and attach it to the relevant resource.

## Environment

```env
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=ap-southeast-1
AWS_S3_BUCKET_NAME=lastbite
AWS_S3_UPLOAD_EXPIRE_SECONDS=300
AWS_S3_MAX_IMAGE_SIZE_MB=5
AWS_S3_MAX_VIDEO_SIZE_MB=50
AWS_S3_PUBLIC_BASE_URL=https://lastbite.s3.amazonaws.com
```

The S3 bucket must not allow public write. The IAM principal used by the backend needs permission for `s3:PutObject` presigning and `s3:HeadObject` checks on the configured bucket.

## S3 CORS

Example CORS configuration for local frontend development:

```json
[
  {
    "AllowedOrigins": ["http://localhost:3000", "http://localhost:5173"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["Content-Type"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

## API flow

1. Request a signed URL.

```bash
curl -X POST http://localhost:8080/api/v1/media/uploads/presigned-url \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "store-front.jpg",
    "contentType": "image/jpeg",
    "fileSize": 1024000,
    "purpose": "STORE_COVER",
    "targetType": "STORE",
    "targetId": "store-uuid"
  }'
```

2. Upload directly to S3 with the same `Content-Type`.

```bash
curl -X PUT "$UPLOAD_URL" \
  -H "Content-Type: image/jpeg" \
  --upload-file ./store-front.jpg
```

3. Confirm the upload.

```bash
curl -X POST http://localhost:8080/api/v1/media/uploads/confirm \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "uploadId": "'"$UPLOAD_ID"'",
    "key": "'"$OBJECT_KEY"'"
  }'
```

## Frontend example

```js
async function uploadMedia(file, purpose, accessToken) {
  const presignRes = await fetch('/api/v1/media/uploads/presigned-url', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      fileName: file.name,
      contentType: file.type,
      fileSize: file.size,
      purpose
    })
  });
  const { result } = await presignRes.json();

  await fetch(result.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file
  });

  const confirmRes = await fetch('/api/v1/media/uploads/confirm', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      uploadId: result.uploadId,
      key: result.key
    })
  });

  return confirmRes.json();
}
```

## Bag images

Use `BAG_IMAGE` when uploading photos for a surprise bag. In this flow, `targetId`
is the `bagId`, and the S3 object key is created under `public/bag/...`.
After the upload is confirmed, the backend appends the uploaded object key to
`surprise_bags.photos`.

```json
{
  "fileName": "bag.jpg",
  "contentType": "image/jpeg",
  "fileSize": 1024000,
  "purpose": "BAG_IMAGE",
  "targetType": "BAG",
  "targetId": "bag-uuid"
}
```
