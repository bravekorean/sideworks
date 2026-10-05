import { useEffect, useState } from 'react'
import { getAdminDocumentTypes, createDocumentType, updateDocumentType } from '../api/documentTypeAdminApi'

export default function DocumentTypeManagementPage() {
  const [types, setTypes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState('')
  const [reload, setReload] = useState(0)
  const [editing, setEditing] = useState(null)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  useEffect(() => {
    let active = true
    getAdminDocumentTypes().then((data) => {
      if (active) { setTypes(data); setError('') }
    }).catch((failure) => {
      if (active) setError(failure.response?.status === 403 ? '시스템 관리자만 접근할 수 있습니다.'
        : failure.response?.data?.message ?? '문서 종류를 불러오지 못했습니다.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [reload])

  function refresh() {
    setEditing(null)
    setLoading(true)
    setReload((value) => value + 1)
  }

  function openEditor(type) {
    if (editing && !window.confirm('작성 중인 입력을 버리고 다른 양식을 열까요?')) return
    setEditing(type)
    setFormError('')
    setFeedback('')
  }

  async function save(event) {
    event.preventDefault()
    if (busy) return
    const data = new FormData(event.currentTarget)
    const request = {
      typeName: data.get('typeName').trim(),
      description: data.get('description').trim(),
      contentTemplate: data.get('contentTemplate'),
      sortOrder: Number(data.get('sortOrder')),
    }
    if (!request.typeName || !Number.isInteger(request.sortOrder) || request.sortOrder < 0) {
      setFormError('이름과 0 이상의 정수 표시 순서를 확인해주세요.')
      return
    }
    const isEdit = editing.approvalDocumentTypeId != null
    if (isEdit) {
      request.active = data.get('active') === 'true'
      request.version = editing.version
      if (editing.active && !request.active && !window.confirm('비활성화하면 새 결재에 사용할 수 없습니다. 기존 결재는 유지됩니다. 계속할까요?')) return
    } else request.typeCode = data.get('typeCode').trim()
    setBusy(true)
    setFormError('')
    try {
      const saved = isEdit ? await updateDocumentType(editing.approvalDocumentTypeId, request) : await createDocumentType(request)
      setTypes((current) => [...current.filter((type) => type.approvalDocumentTypeId !== saved.approvalDocumentTypeId), saved]
        .sort((a, b) => a.sortOrder - b.sortOrder || a.approvalDocumentTypeId - b.approvalDocumentTypeId))
      setEditing(null)
      setFeedback(isEdit ? '문서 종류를 수정했습니다.' : '문서 종류를 생성했습니다.')
    } catch (failure) {
      setFormError(failure.response?.status === 403 ? '관리 권한이 없습니다.'
        : failure.response?.data?.message ?? '저장하지 못했습니다.')
    } finally { setBusy(false) }
  }

  return <div className="document-type-management-page">
    <header className="page-header">
      <div><span className="section-kicker">ADMIN · APPROVAL</span><h1>문서 종류 관리</h1>
        <p>일반 문서의 본문 초안·표시 순서·활성 상태를 관리합니다. 기존 결재 본문은 변경되지 않습니다.</p></div>
      <button className="admin-primary-button" disabled={loading || Boolean(error) || busy} onClick={() => openEditor({})} type="button">+ 문서 종류 생성</button>
    </header>
    <p className="compose-feedback" role="status">{feedback}</p>
    <section className="panel document-type-list">
      {loading ? <p className="document-type-message">불러오는 중입니다.</p> : error
        ? <div className="document-type-message" role="alert"><p>{error}</p><button className="admin-primary-button" onClick={refresh} type="button">다시 조회</button></div>
        : <>
          <div className="document-type-toolbar"><span>총 {types.length}개 · 시스템 종류는 읽기 전용입니다.</span><button className="text-button" disabled={busy} onClick={() => { if (!editing || window.confirm('입력을 버리고 다시 조회할까요?')) refresh() }} type="button">새로 조회</button></div>
          {types.length === 0 && <p className="document-type-message">등록된 문서 종류가 없습니다.</p>}
          {types.map((type) => <article className="document-type-row" key={type.approvalDocumentTypeId}>
            <div><strong>{type.typeName}</strong><small>{type.typeCode} · 표시 순서 {type.sortOrder}</small><p>{type.description || '설명 없음'}</p></div>
            <span>{type.active ? '활성' : '비활성'} · {type.system ? '시스템' : '일반'}</span>
            <button className="admin-primary-button" disabled={type.system || type.behaviorType !== 'GENERAL' || busy} onClick={() => openEditor(type)} type="button">수정</button>
          </article>)}
        </>}
    </section>
    {editing && <section className="panel document-type-editor" aria-labelledby="document-type-editor-title">
      <h2 id="document-type-editor-title">{editing.approvalDocumentTypeId ? '문서 종류 수정' : '문서 종류 생성'}</h2>
      <form key={editing.approvalDocumentTypeId ?? 'new'} onSubmit={save}>
        <fieldset disabled={busy}>
          <div className="compose-form-grid">
            <label className="form-field"><span>코드 (생성 후 변경 불가)</span><input name="typeCode" required pattern="[A-Z][A-Z0-9_]{0,29}" maxLength={30} readOnly={Boolean(editing.approvalDocumentTypeId)} defaultValue={editing.typeCode ?? ''} placeholder="예: ESTIMATE" /><small>영문 대문자로 시작 · 대문자, 숫자, 밑줄</small></label>
            <label className="form-field"><span>문서 이름</span><input name="typeName" required maxLength={100} defaultValue={editing.typeName ?? ''} /></label>
          </div>
          <label className="form-field"><span>설명</span><input name="description" maxLength={500} defaultValue={editing.description ?? ''} /></label>
          <label className="form-field"><span>본문 초안 (최대 20,000자)</span><textarea name="contentTemplate" maxLength={20000} defaultValue={editing.contentTemplate ?? ''} placeholder="결재 내용 입력란에 미리 채울 문구" /></label>
          <div className="compose-form-grid">
            <label className="form-field"><span>표시 순서 (작을수록 먼저 표시)</span><input name="sortOrder" type="number" required min={0} max={2147483647} step={1} defaultValue={editing.sortOrder ?? 0} /></label>
            {editing.approvalDocumentTypeId && <label className="form-field"><span>활성 상태</span><select name="active" defaultValue={String(editing.active)}><option value="true">활성</option><option value="false">비활성</option></select></label>}
          </div>
          <p role="alert" className="compose-feedback">{formError}</p>
          <div className="document-type-actions"><button className="admin-primary-button" type="submit">{busy ? '저장 중...' : '저장'}</button><button className="text-button" onClick={() => setEditing(null)} type="button">취소</button><button className="text-button" onClick={() => { if (window.confirm('입력을 버리고 최신 목록을 조회할까요?')) refresh() }} type="button">최신 목록 다시 조회</button></div>
        </fieldset>
      </form>
    </section>}
  </div>
}
