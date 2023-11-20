{-# LANGUAGE DuplicateRecordFields #-}
{-# LANGUAGE NamedFieldPuns #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE QuasiQuotes #-}

module Simplex.Chat.Store.Remote where

import Control.Monad.Except
import Data.Int (Int64)
import Data.Text (Text)
import Database.SQLite.Simple (Only (..))
import qualified Database.SQLite.Simple as SQL
import Database.SQLite.Simple.QQ (sql)
import Simplex.Chat.Remote.Types
import Simplex.Chat.Store.Shared
import Simplex.Messaging.Agent.Store.SQLite (firstRow, maybeFirstRow)
import qualified Simplex.Messaging.Agent.Store.SQLite.DB as DB
import qualified Simplex.Messaging.Crypto as C
import Simplex.RemoteControl.Types
import UnliftIO
import Simplex.Chat.Types (RemoteHostId)

insertRemoteHost :: DB.Connection -> Text -> FilePath -> RCHostPairing -> ExceptT StoreError IO RemoteHostId
insertRemoteHost db hostDeviceName storePath RCHostPairing {caKey, caCert, idPrivKey, knownHost = kh_} = do
  KnownHostPairing {hostFingerprint, hostDhPubKey} <-
    maybe (throwError SERemoteHostUnknown) pure kh_
  checkConstraint SERemoteHostDuplicateCA . liftIO $
    DB.execute
      db
      [sql|
        INSERT INTO remote_hosts
          (host_device_name, store_path, ca_key, ca_cert, id_key, host_fingerprint, host_dh_pub)
        VALUES
          (?, ?, ?, ?, ?, ?, ?)
      |]
      (hostDeviceName, storePath, caKey, C.SignedObject caCert, idPrivKey, hostFingerprint, hostDhPubKey)
  liftIO $ insertedRowId db

getRemoteHosts :: DB.Connection -> IO [RemoteHost]
getRemoteHosts db =
  map toRemoteHost <$> DB.query_ db remoteHostQuery

getRemoteHost :: DB.Connection -> RemoteHostId -> ExceptT StoreError IO RemoteHost
getRemoteHost db remoteHostId =
  ExceptT . firstRow toRemoteHost (SERemoteHostNotFound remoteHostId) $
    DB.query db (remoteHostQuery <> " WHERE remote_host_id = ?") (Only remoteHostId)

getRemoteHostByFingerprint :: DB.Connection -> C.KeyHash -> IO (Maybe RemoteHost)
getRemoteHostByFingerprint db fingerprint =
  maybeFirstRow toRemoteHost $
    DB.query db (remoteHostQuery <> " WHERE host_fingerprint = ?") (Only fingerprint)

remoteHostQuery :: SQL.Query
remoteHostQuery =
  [sql|
    SELECT remote_host_id, host_device_name, store_path, ca_key, ca_cert, id_key, host_fingerprint, host_dh_pub
    FROM remote_hosts
  |]

toRemoteHost :: (Int64, Text, FilePath, C.APrivateSignKey, C.SignedObject C.Certificate, C.PrivateKeyEd25519, C.KeyHash, C.PublicKeyX25519) -> RemoteHost
toRemoteHost (remoteHostId, hostDeviceName, storePath, caKey, C.SignedObject caCert, idPrivKey, hostFingerprint, hostDhPubKey) =
  RemoteHost {remoteHostId, hostDeviceName, storePath, hostPairing}
  where
    hostPairing = RCHostPairing {caKey, caCert, idPrivKey, knownHost = Just knownHost}
    knownHost = KnownHostPairing {hostFingerprint, hostDhPubKey}

updateHostPairing :: DB.Connection -> RemoteHostId -> Text -> C.PublicKeyX25519 -> IO ()
updateHostPairing db rhId hostDeviceName hostDhPubKey =
  DB.execute
    db
    [sql|
      UPDATE remote_hosts
      SET host_device_name = ?, host_dh_pub = ?
      WHERE remote_host_id = ?
    |]
    (hostDeviceName, hostDhPubKey, rhId)

deleteRemoteHostRecord :: DB.Connection -> RemoteHostId -> IO ()
deleteRemoteHostRecord db remoteHostId = DB.execute db "DELETE FROM remote_hosts WHERE remote_host_id = ?" (Only remoteHostId)

insertRemoteCtrl :: DB.Connection -> Text -> RCCtrlPairing -> ExceptT StoreError IO RemoteCtrlId
insertRemoteCtrl db ctrlDeviceName RCCtrlPairing {caKey, caCert, ctrlFingerprint, idPubKey, dhPrivKey, prevDhPrivKey} = do
  checkConstraint SERemoteCtrlDuplicateCA . liftIO $
    DB.execute
      db
      [sql|
      INSERT INTO remote_controllers
        (ctrl_device_name, ca_key, ca_cert, ctrl_fingerprint, id_pub, dh_priv_key, prev_dh_priv_key)
      VALUES
        (?, ?, ?, ?, ?, ?, ?)
    |]
      (ctrlDeviceName, caKey, C.SignedObject caCert, ctrlFingerprint, idPubKey, dhPrivKey, prevDhPrivKey)
  liftIO $ insertedRowId db

getRemoteCtrls :: DB.Connection -> IO [RemoteCtrl]
getRemoteCtrls db =
  map toRemoteCtrl <$> DB.query_ db remoteCtrlQuery

getRemoteCtrl :: DB.Connection -> RemoteCtrlId -> ExceptT StoreError IO RemoteCtrl
getRemoteCtrl db remoteCtrlId =
  ExceptT . firstRow toRemoteCtrl (SERemoteCtrlNotFound remoteCtrlId) $
    DB.query db (remoteCtrlQuery <> " WHERE remote_ctrl_id = ?") (Only remoteCtrlId)

getRemoteCtrlByFingerprint :: DB.Connection -> C.KeyHash -> IO (Maybe RemoteCtrl)
getRemoteCtrlByFingerprint db fingerprint =
  maybeFirstRow toRemoteCtrl $
    DB.query db (remoteCtrlQuery <> " WHERE ctrl_fingerprint = ?") (Only fingerprint)

remoteCtrlQuery :: SQL.Query
remoteCtrlQuery =
  [sql|
    SELECT remote_ctrl_id, ctrl_device_name, ca_key, ca_cert, ctrl_fingerprint, id_pub, dh_priv_key, prev_dh_priv_key
    FROM remote_controllers
  |]

toRemoteCtrl ::
  ( RemoteCtrlId,
    Text,
    C.APrivateSignKey,
    C.SignedObject C.Certificate,
    C.KeyHash,
    C.PublicKeyEd25519,
    C.PrivateKeyX25519,
    Maybe C.PrivateKeyX25519
  ) ->
  RemoteCtrl
toRemoteCtrl (remoteCtrlId, ctrlDeviceName, caKey, C.SignedObject caCert, ctrlFingerprint, idPubKey, dhPrivKey, prevDhPrivKey) =
  let ctrlPairing = RCCtrlPairing {caKey, caCert, ctrlFingerprint, idPubKey, dhPrivKey, prevDhPrivKey}
   in RemoteCtrl {remoteCtrlId, ctrlDeviceName, ctrlPairing}

updateRemoteCtrl :: DB.Connection -> RemoteCtrl -> Text -> C.PrivateKeyX25519 -> IO ()
updateRemoteCtrl db RemoteCtrl {remoteCtrlId} ctrlDeviceName dhPrivKey =
  DB.execute
    db
    [sql|
      UPDATE remote_controllers
      SET ctrl_device_name = ?, dh_priv_key = ?, prev_dh_priv_key = dh_priv_key
      WHERE remote_ctrl_id = ?
    |]
    (ctrlDeviceName, dhPrivKey, remoteCtrlId)

deleteRemoteCtrlRecord :: DB.Connection -> RemoteCtrlId -> IO ()
deleteRemoteCtrlRecord db remoteCtrlId =
  DB.execute db "DELETE FROM remote_controllers WHERE remote_ctrl_id = ?" (Only remoteCtrlId)
