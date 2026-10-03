-- android_metadata
CREATE TABLE android_metadata (locale TEXT);

-- room_master_table
CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);

-- sqlite_sequence
CREATE TABLE sqlite_sequence(name,seq);

-- events
CREATE TABLE `events` (`id` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `source` TEXT NOT NULL, `source_package` TEXT NOT NULL, `app_name` TEXT NOT NULL, `title` TEXT, `text` TEXT, `sub_text` TEXT, `event_type` TEXT NOT NULL, `category` TEXT NOT NULL, `confidence` REAL NOT NULL, `is_ongoing` INTEGER NOT NULL DEFAULT 0, `media_track` TEXT, `media_artist` TEXT, `media_playback_state` TEXT, `content_fingerprint` TEXT NOT NULL, `raw_payload` TEXT NOT NULL, `normalized_text` TEXT NOT NULL, `structured_data` TEXT NOT NULL, `is_user_corrected` INTEGER NOT NULL DEFAULT 0, `embedding` BLOB, PRIMARY KEY(`id`));

-- idx_event_pkg_time
CREATE INDEX `idx_event_pkg_time` ON `events` (`source_package`, `timestamp`);

-- idx_event_cat_time
CREATE INDEX `idx_event_cat_time` ON `events` (`category`, `timestamp`);

-- idx_event_type_time
CREATE INDEX `idx_event_type_time` ON `events` (`event_type`, `timestamp`);

-- idx_event_fingerprint
CREATE INDEX `idx_event_fingerprint` ON `events` (`content_fingerprint`);

-- idx_event_confidence
CREATE INDEX `idx_event_confidence` ON `events` (`confidence`);

-- idx_event_corrected
CREATE INDEX `idx_event_corrected` ON `events` (`is_user_corrected`);

-- prototypes
CREATE TABLE `prototypes` (`id` TEXT NOT NULL, `category` TEXT NOT NULL, `subCategory` TEXT NOT NULL, `label` TEXT NOT NULL, `canonicalText` TEXT NOT NULL, `embeddingBlob` BLOB NOT NULL, `weight` REAL NOT NULL, `supportCount` INTEGER NOT NULL, `scope` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`));

-- index_prototypes_category
CREATE INDEX `index_prototypes_category` ON `prototypes` (`category`);

-- index_prototypes_subCategory
CREATE INDEX `index_prototypes_subCategory` ON `prototypes` (`subCategory`);

-- index_prototypes_scope_category
CREATE INDEX `index_prototypes_scope_category` ON `prototypes` (`scope`, `category`);

-- music_tracks
CREATE TABLE `music_tracks` (`trackKey` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `playCount` INTEGER NOT NULL, `totalDurationMs` INTEGER NOT NULL, `firstPlayedAt` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, `userNotes` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `syncedLyrics` TEXT, `plainLyrics` TEXT, PRIMARY KEY(`trackKey`));

-- index_music_tracks_isFavorite
CREATE INDEX `index_music_tracks_isFavorite` ON `music_tracks` (`isFavorite`);

-- index_music_tracks_playCount
CREATE INDEX `index_music_tracks_playCount` ON `music_tracks` (`playCount`);

-- index_music_tracks_lastPlayedAt
CREATE INDEX `index_music_tracks_lastPlayedAt` ON `music_tracks` (`lastPlayedAt`);

-- music_listening_sessions
CREATE TABLE `music_listening_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackKey` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `startTimeMs` INTEGER NOT NULL, `endTimeMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL);

-- index_music_listening_sessions_trackKey
CREATE INDEX `index_music_listening_sessions_trackKey` ON `music_listening_sessions` (`trackKey`);

-- index_music_listening_sessions_startTimeMs
CREATE INDEX `index_music_listening_sessions_startTimeMs` ON `music_listening_sessions` (`startTimeMs`);

-- financial_transactions
CREATE TABLE `financial_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` TEXT NOT NULL, `direction` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `rawMerchant` TEXT, `normalizedMerchant` TEXT, `category` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `sourcePackage` TEXT NOT NULL, `accountMask` TEXT);

-- index_financial_transactions_eventId
CREATE UNIQUE INDEX `index_financial_transactions_eventId` ON `financial_transactions` (`eventId`);

-- index_financial_transactions_direction
CREATE INDEX `index_financial_transactions_direction` ON `financial_transactions` (`direction`);

-- index_financial_transactions_timestamp
CREATE INDEX `index_financial_transactions_timestamp` ON `financial_transactions` (`timestamp`);

-- index_financial_transactions_normalizedMerchant
CREATE INDEX `index_financial_transactions_normalizedMerchant` ON `financial_transactions` (`normalizedMerchant`);

-- index_financial_transactions_category
CREATE INDEX `index_financial_transactions_category` ON `financial_transactions` (`category`);

-- study_items
CREATE TABLE `study_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceEventId` TEXT NOT NULL, `type` TEXT NOT NULL, `subject` TEXT NOT NULL, `title` TEXT NOT NULL, `dueAtEpochMs` INTEGER, `location` TEXT NOT NULL, `status` TEXT NOT NULL, `reminderOffsetMinutes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL);

-- index_study_items_sourceEventId
CREATE INDEX `index_study_items_sourceEventId` ON `study_items` (`sourceEventId`);

-- index_study_items_type
CREATE INDEX `index_study_items_type` ON `study_items` (`type`);

-- index_study_items_status
CREATE INDEX `index_study_items_status` ON `study_items` (`status`);

-- index_study_items_dueAtEpochMs
CREATE INDEX `index_study_items_dueAtEpochMs` ON `study_items` (`dueAtEpochMs`);

