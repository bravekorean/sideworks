import { useEffect, useState } from 'react'
import { getMyProfile } from '../api/userApi'
import {
  createTemplate, deleteTemplate, getManagedTemplates, getTemplateDepartments, getTemplateMembers, updateTemplate,
} from '../api/approvalTemplateApi'

const emptyForm = { templateName: '', description: '', scope: 'DEPARTMENT', departmentId: '',
  status: 'ACTIVE', approverIds: [], ccUserIds: [] }

export default function ApprovalTemplateManagementPage() {
  const [templates, setTemplates] = useState([])
  const [departments, setDepartments] = useState([])
  const [users, setUsers] = useState([])
  const [referenceUsers, setReferenceUsers] = useState([])
  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState(null)
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const [pageIndex, setPageIndex] = useState(0)
  const [pageCount, setPageCount] = useState(0)
  const [memberPage, setMemberPage] = useState(0)
  const [memberPageCount, setMemberPageCount] = useState(0)
  const [knownMembers, setKnownMembers] = useState([])
  const [memberLoading, setMemberLoading] = useState(false)
  const [memberError, setMemberError] = useState('')
  const [memberReload, setMemberReload] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([getManagedTemplates(pageIndex), getTemplateDepartments(), getMyProfile()])
      .then(([page, managedDepartments, me]) => {
        if (!active) return
        setTemplates(page.content)
        setPageCount(page.totalPages)
        setDepartments(managedDepartments)
        setProfile(me)
        setError('')
      }).catch((failure) => {
        if (active) setError(failure.response?.status === 403 ? '결재선 템플릿 관리 권한이 없습니다.'
          : failure.response?.data?.message ?? '템플릿 목록을 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [reload, pageIndex])

  const memberScope = form?.scope
  const memberDepartmentId = form?.departmentId
  useEffect(() => {
    if (!memberScope || (memberScope === 'DEPARTMENT' && !memberDepartmentId)) return undefined
    let active = true
    Promise.all([
      getTemplateMembers(memberScope, memberDepartmentId, memberPage),
      getTemplateMembers(memberScope, memberDepartmentId, memberPage, 100, true),
    ]).then(([page, referencePage]) => {
        if (!active) return
        setUsers(page.content)
        setReferenceUsers(referencePage.content)
        setMemberPageCount(Math.max(page.totalPages, referencePage.totalPages))
        const loaded = [...page.content, ...referencePage.content]
        setKnownMembers((previous) => [...previous.filter((member) =>
          !loaded.some((candidate) => candidate.userId === member.userId)),
          ...loaded.filter((member, index) => loaded.findIndex((candidate) => candidate.userId === member.userId) === index)])
      }).catch((failure) => {
        if (active) {
          setUsers([])
          setReferenceUsers([])
          setMemberError(failure.response?.data?.message ?? '부서 구성원을 불러오지 못했습니다.')
        }
      }).finally(() => { if (active) setMemberLoading(false) })
    return () => { active = false }
  }, [memberScope, memberDepartmentId, memberPage, memberReload])

  const refresh = () => {
    setPageIndex(0)
    setReload((value) => value + 1)
  }

  const openNew = () => {
    setMemberLoading(Boolean(departments[0]?.departmentId))
    setMemberError('')
    setMemberReload((value) => value + 1)
    setUsers([])
    setReferenceUsers([])
    setKnownMembers([])
    setMemberPage(0)
    setEditing(null)
    setForm({ ...emptyForm, departmentId: String(departments[0]?.departmentId ?? '') })
    setMessage('')
  }

  const openEdit = (template) => {
    setMemberLoading(true)
    setMemberError('')
    setMemberReload((value) => value + 1)
    setUsers([])
    setReferenceUsers([])
    setKnownMembers([])
    setMemberPage(0)
    setEditing(template)
    setForm({ templateName: template.templateName, description: template.description ?? '',
      scope: template.scope, departmentId: String(template.departmentId ?? ''), status: template.status,
      approverIds: template.approvers.map((item) => item.userId),
      ccUserIds: template.ccUsers.map((item) => item.userId) })
    setMessage('')
  }

  const selectedMembers = editing ? [...editing.approvers, ...editing.ccUsers] : []
  const savedMembers = [...knownMembers, ...selectedMembers.filter((member) =>
    !knownMembers.some((user) => user.userId === member.userId))]
  const choices = [...users, ...savedMembers.filter((member) =>
    !users.some((user) => user.userId === member.userId)
    && form?.approverIds.includes(member.userId))]
  const referenceChoices = [...referenceUsers, ...savedMembers.filter((member) =>
    !referenceUsers.some((user) => user.userId === member.userId) && form?.ccUserIds.includes(member.userId))]

  const changeScope = (scope, departmentId) => {
    setMemberLoading(scope === 'COMMON' || Boolean(departmentId))
    setMemberError('')
    setMemberPageCount(0)
    setUsers([])
    setReferenceUsers([])
    setKnownMembers([])
    setMemberPage(0)
    setForm((current) => ({ ...current, scope, departmentId, approverIds: [], ccUserIds: [] }))
  }

  const changeMemberPage = (page) => {
    setMemberLoading(true)
    setMemberError('')
    setMemberPage(page)
  }

  const toggleMember = (type, userId) => {
    const own = type === 'approverIds' ? 'approverIds' : 'ccUserIds'
    const other = type === 'approverIds' ? 'ccUserIds' : 'approverIds'
    if (form[other].includes(userId)) {
      setMessage('한 사람을 결재자와 참조자로 겸임할 수 없습니다.')
      return
    }
    setForm((current) => ({ ...current, [own]: current[own].includes(userId)
      ? current[own].filter((id) => id !== userId) : [...current[own], userId] }))
    setMessage('')
  }

  const moveApprover = (index, direction) => {
    setForm((current) => {
      const ids = [...current.approverIds]
      const target = index + direction
      if (target < 0 || target >= ids.length) return current
      ;[ids[index], ids[target]] = [ids[target], ids[index]]
      return { ...current, approverIds: ids }
    })
  }

  const save = async (event) => {
    event.preventDefault()
    if (busy || memberLoading || memberError) return
    if (!form.templateName.trim() || form.approverIds.length === 0
        || (form.scope === 'DEPARTMENT' && !form.departmentId)) {
      setMessage('이름, 소유 부서, 결재자를 확인해주세요.')
      return
    }
    const request = { ...form, templateName: form.templateName.trim(),
      description: form.description.trim(),
      departmentId: form.scope === 'COMMON' ? null : Number(form.departmentId),
      version: editing?.version ?? null }
    setBusy(true)
    try {
      if (editing) await updateTemplate(editing.approvalTemplateId, request)
      else await createTemplate(request)
      setForm(null)
      setEditing(null)
      setMessage(editing ? '템플릿을 수정하고 인사정보를 재검증했습니다.' : '템플릿을 생성했습니다.')
      refresh()
    } catch (failure) {
      setMessage(failure.response?.data?.message ?? '템플릿을 저장하지 못했습니다.')
    } finally { setBusy(false) }
  }

  const remove = async (template) => {
    if (!window.confirm(`‘${template.templateName}’ 템플릿을 삭제할까요? 삭제 후에는 복구할 수 없습니다. 이미 상신한 문서는 유지됩니다.`)) return
    setBusy(true)
    try {
      await deleteTemplate(template.approvalTemplateId, template.version)
      setMessage('템플릿을 삭제했습니다.')
      setForm(null)
      refresh()
    } catch (failure) {
      setMessage(failure.response?.data?.message ?? '템플릿을 삭제하지 못했습니다.')
    } finally { setBusy(false) }
  }

  return <div className="document-type-management-page">
    <header className="page-header"><div><span className="section-kicker">APPROVAL · TEMPLATE</span>
      <h1>결재선 템플릿 관리</h1><p>공용 또는 담당 부서의 결재자·참조자 구성을 관리합니다.</p></div>
      <button className="admin-primary-button" disabled={busy || Boolean(error)} onClick={openNew} type="button">+ 템플릿 생성</button>
    </header>
    {error && <p role="alert" className="compose-feedback">{error}</p>}
    {message && <p role="status" className="compose-feedback">{message}</p>}
    <section className="panel document-type-list">
      <div className="document-type-toolbar"><span>관리 가능한 템플릿 {templates.length}개</span>
        <button className="text-button" onClick={refresh} type="button">새로 조회</button></div>
      {templates.length === 0 && !error && <p className="document-type-message">등록된 템플릿이 없습니다.</p>}
      {templates.map((template) => <article className="document-type-row" key={template.approvalTemplateId}>
        <div><strong>{template.templateName}</strong>
          <small>{template.scope === 'COMMON' ? '공용' : template.departmentName} · {template.status === 'ACTIVE' ? '활성' : '비활성'} · 버전 {template.version}</small>
          <p>{template.approvers.map((member) => member.userName).join(' → ')}
            {template.ccUsers.length > 0 && ` · 참조 ${template.ccUsers.map((member) => member.userName).join(', ')}`}</p>
          {template.validationStatus === 'BLOCKED' && <p role="alert">⚠ 사용 차단 · {template.blockReason}</p>}
        </div>
        <button className="admin-primary-button" disabled={busy} onClick={() => openEdit(template)} type="button">수정</button>
        <button className="compose-button compose-button--danger" disabled={busy} onClick={() => remove(template)} type="button">삭제</button>
      </article>)}
      {pageCount > 1 && <div className="document-type-toolbar">
        <button className="text-button" disabled={pageIndex === 0} onClick={() => setPageIndex(pageIndex - 1)} type="button">이전</button>
        <span>{pageIndex + 1} / {pageCount}</span>
        <button className="text-button" disabled={pageIndex + 1 >= pageCount} onClick={() => setPageIndex(pageIndex + 1)} type="button">다음</button>
      </div>}
    </section>
    {form && <section className="panel document-type-editor"><h2>{editing ? '템플릿 수정' : '템플릿 생성'}</h2>
      <form onSubmit={save}><fieldset disabled={busy || memberLoading}>
        <div className="compose-form-grid">
          <label className="form-field"><span>이름</span><input required maxLength={100} value={form.templateName}
            onChange={(event) => setForm({ ...form, templateName: event.target.value })} /></label>
          <label className="form-field"><span>공개 범위</span><select value={form.scope}
            onChange={(event) => changeScope(event.target.value, event.target.value === 'COMMON' ? '' : String(departments[0]?.departmentId ?? ''))}>
            <option value="DEPARTMENT">부서</option>{profile?.userRole === 'SUPER_ADMIN' && <option value="COMMON">공용</option>}
          </select></label>
        </div>
        {form.scope === 'DEPARTMENT' && <label className="form-field"><span>소유 부서</span><select required value={form.departmentId}
          onChange={(event) => changeScope(form.scope, event.target.value)}>
          <option value="">부서 선택</option>{departments.map((department) => <option key={department.departmentId}
            value={department.departmentId}>{department.departmentName}</option>)}
        </select></label>}
        <label className="form-field"><span>설명</span><input maxLength={500} value={form.description}
          onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
        <label className="form-field"><span>활성 상태</span><select value={form.status}
          onChange={(event) => setForm({ ...form, status: event.target.value })}>
          <option value="ACTIVE">활성</option><option value="INACTIVE">비활성</option>
        </select></label>
        <h3>결재자 · 선택 순서가 결재 순서입니다</h3>
        <p>{form.scope === 'DEPARTMENT' ? '소유 부서의 활성 구성원(본인 포함)을 선택합니다.' : '전사 활성 구성원(본인 포함)을 선택합니다.'}</p>
        {memberLoading && <p>구성원을 불러오는 중입니다.</p>}
        {memberError && <p role="alert">{memberError}</p>}
        {!memberLoading && !memberError && choices.length === 0 && <p>선택 가능한 활성 구성원이 없습니다.</p>}
        <div className="cc-chip-list">{choices.map((user) => <label
          className={`cc-select-chip ${form.approverIds.includes(user.userId) ? 'cc-select-chip--selected' : ''}`} key={user.userId}>
          <input type="checkbox" checked={form.approverIds.includes(user.userId)} onChange={() => toggleMember('approverIds', user.userId)} />
          <span>{user.userName}{user.userId === profile?.userId ? ' (본인)' : ''}</span><small>{user.departmentName ?? '부서 미배정'}</small>
        </label>)}</div>
        <ol>{form.approverIds.map((id, index) => <li key={id}>{choices.find((user) => user.userId === id)?.userName ?? id}
          <button type="button" disabled={index === 0} onClick={() => moveApprover(index, -1)}>↑</button>
          <button type="button" disabled={index === form.approverIds.length - 1} onClick={() => moveApprover(index, 1)}>↓</button>
        </li>)}</ol>
        <h3>참조자 · {form.ccUserIds.length}명 선택</h3><p>부서와 관계없이 전사 활성 구성원을 선택합니다.</p>
        <div className="cc-chip-list">{referenceChoices.map((user) => <label
          className={`cc-select-chip ${form.ccUserIds.includes(user.userId) ? 'cc-select-chip--selected' : ''}`} key={user.userId}>
          <input type="checkbox" checked={form.ccUserIds.includes(user.userId)} onChange={() => toggleMember('ccUserIds', user.userId)} />
          <span>{user.userName}</span><small>{user.departmentName ?? '부서 미배정'}</small>
        </label>)}</div>
        {message && <p role="status" className="compose-feedback">{message}</p>}
        {memberPageCount > 1 && <div className="document-type-toolbar">
          <button type="button" disabled={memberPage === 0} onClick={() => changeMemberPage(memberPage - 1)}>구성원 이전</button>
          <span>{memberPage + 1} / {memberPageCount}</span>
          <button type="button" disabled={memberPage + 1 >= memberPageCount} onClick={() => changeMemberPage(memberPage + 1)}>구성원 다음</button>
        </div>}
        <div className="document-type-actions"><button className="admin-primary-button" disabled={Boolean(memberError)} type="submit">{busy ? '저장 중...' : '저장'}</button>
          <button className="text-button" type="button" onClick={() => setForm(null)}>취소</button></div>
      </fieldset></form>
    </section>}
  </div>
}
