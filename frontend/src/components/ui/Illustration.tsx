import { useState, type CSSProperties } from 'react'
import { ILLUSTRATIONS, type IllustrationName } from '../../design/assets'

// 모든 일러스트는 이 컴포넌트로만 그린다. 파일이 없으면 영역을 paper 색으로 비운다.
export default function Illustration({
  name,
  alt = '',
  className = '',
  style,
}: {
  name: IllustrationName
  alt?: string
  className?: string
  style?: CSSProperties
}) {
  const [failed, setFailed] = useState(false)
  if (failed) return <div className={`bg-paper ${className}`} style={style} aria-hidden="true" />
  return (
    <img
      src={ILLUSTRATIONS[name]}
      alt={alt}
      aria-hidden={alt === '' ? true : undefined}
      onError={() => setFailed(true)}
      style={style}
      className={`block object-cover ${className}`}
    />
  )
}
