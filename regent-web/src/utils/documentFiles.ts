export const ACCEPTED_DOCUMENT_EXTENSIONS = [
  '.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx',
  '.odt', '.ods', '.odp', '.txt', '.md', '.csv', '.json',
  '.yaml', '.yml', '.xml', '.rtf',
]

type FileName = {
  name: string
}

type ClipboardItemLike = {
  kind: string
  getAsFile: () => File | null
}

type ClipboardDataLike = {
  items?: ArrayLike<ClipboardItemLike>
  files?: ArrayLike<File>
}

export function documentExtension(filename: string): string {
  const dot = filename.lastIndexOf('.')
  return dot < 0 ? '' : filename.slice(dot).toLowerCase()
}

export function isDocumentFile(file: FileName): boolean {
  return ACCEPTED_DOCUMENT_EXTENSIONS.includes(documentExtension(file.name))
}

export function filesFromClipboard(data: ClipboardDataLike | null | undefined): File[] {
  if (!data) return []
  const fromItems = Array.from(data.items ?? [])
    .filter((item) => item.kind === 'file')
    .map((item) => item.getAsFile())
    .filter((file): file is File => file !== null)
  return fromItems.length > 0 ? fromItems : Array.from(data.files ?? [])
}
