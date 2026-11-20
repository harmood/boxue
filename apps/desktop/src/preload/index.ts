import { contextBridge } from 'electron'

contextBridge.exposeInMainWorld('boxue', {
  platform: process.platform,
  electronVersion: process.versions.electron,
  ping: () => 'pong'
})