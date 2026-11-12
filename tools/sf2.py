"""Minimal SoundFont 2 reader: presets, their zones (merged preset + instrument generators) and sample data."""
import struct

GEN = dict(startAddrsOffset=0, endAddrsOffset=1, startloopAddrsOffset=2, endloopAddrsOffset=3, startAddrsCoarseOffset=4,
           pan=17, attackVolEnv=34, holdVolEnv=35, decayVolEnv=36, sustainVolEnv=37, releaseVolEnv=38, instrument=41,
           keyRange=43, velRange=44, endAddrsCoarseOffset=12, startloopAddrsCoarseOffset=45, initialAttenuation=48,
           endloopAddrsCoarseOffset=50, coarseTune=51, fineTune=52, sampleID=53, sampleModes=54, scaleTuning=56,
           overridingRootKey=58)
RANGE = {43, 44}

class SF2:
    def __init__(self, path):
        self.f = open(path, 'rb')
        data = self.f.read(12)
        assert data[:4] == b'RIFF' and data[8:12] == b'sfbk'
        self.chunks = {}
        self._walk(12, struct.unpack('<I', data[4:8])[0] + 8)
        self._parse_pdta()

    def _walk(self, pos, end):
        while pos < end:
            self.f.seek(pos)
            cid, size = struct.unpack('<4sI', self.f.read(8))
            if cid == b'LIST':
                self._walk(pos + 12, pos + 8 + size)
            else:
                self.chunks[cid.decode()] = (pos + 8, size)
            pos += 8 + size + (size & 1)

    def _read(self, name):
        pos, size = self.chunks[name]
        self.f.seek(pos)
        return self.f.read(size)

    def _parse_pdta(self):
        phdr = self._read('phdr'); self.phdr = [struct.unpack('<20sHHHIII', phdr[i:i + 38]) for i in range(0, len(phdr), 38)]
        self.pbag = [struct.unpack('<HH', b) for b in chunks(self._read('pbag'), 4)]
        self.pgen = [struct.unpack('<Hh', b) for b in chunks(self._read('pgen'), 4)]
        self.inst = [struct.unpack('<20sH', b) for b in chunks(self._read('inst'), 22)]
        self.ibag = [struct.unpack('<HH', b) for b in chunks(self._read('ibag'), 4)]
        self.igen = [struct.unpack('<Hh', b) for b in chunks(self._read('igen'), 4)]
        self.shdr = [struct.unpack('<20sIIIIIBbHH', b) for b in chunks(self._read('shdr'), 46)]
        self.smpl_pos = self.chunks['smpl'][0]

    def presets(self):
        return [(name.split(b'\0')[0].decode('latin1'), bank, prog) for (name, prog, bank, *_ ) in self.phdr[:-1]]

    @staticmethod
    def _gens(gens):
        out = {}
        for op, amt in gens:
            out[op] = (amt & 0xFF, (amt >> 8) & 0xFF) if op in RANGE else amt
        return out

    def _zones(self, bags, gens, first, last):
        zones = []
        for b in range(first, last):
            zones.append(self._gens(gens[bags[b][0]:bags[b + 1][0]]))
        return zones

    def zones(self, bank, prog):
        """Zones of a preset: dicts with sample info and merged generators."""
        idx = next(i for i, p in enumerate(self.phdr[:-1]) if p[2] == bank and p[1] == prog)
        pz = self._zones(self.pbag, self.pgen, self.phdr[idx][3], self.phdr[idx + 1][3])
        pglobal = pz[0] if pz and 41 not in pz[0] else {}
        out = []
        for z in pz:
            if 41 not in z: continue
            pzone = dict(pglobal); pzone.update(z)
            i = z[41]
            iz = self._zones(self.ibag, self.igen, self.inst[i][1], self.inst[i + 1][1])
            iglobal = iz[0] if iz and 53 not in iz[0] else {}
            for zz in iz:
                if 53 not in zz: continue
                g = dict(iglobal); g.update(zz)
                # preset generators add to instrument ones (ranges intersect)
                for op, v in pzone.items():
                    if op in (41,): continue
                    if op in RANGE:
                        lo, hi = g.get(op, (0, 127)); g[op] = (max(lo, v[0]), min(hi, v[1]))
                    elif op not in (53, 58, 54, 0, 1, 2, 3, 4, 12, 45, 50):
                        g[op] = g.get(op, 0) + v
                k = g.get(43, (0, 127)); v = g.get(44, (0, 127))
                if k[0] > k[1] or v[0] > v[1]: continue
                out.append(g)
        return out

    def sample(self, sid):
        name, start, end, sl, el, rate, key, corr, link, typ = self.shdr[sid]
        return dict(name=name.split(b'\0')[0].decode('latin1'), start=start, end=end, loop_start=sl, loop_end=el,
                    rate=rate, key=key, corr=corr, type=typ)

    def pcm(self, start, end):
        import numpy as np
        self.f.seek(self.smpl_pos + 2 * start)
        return np.frombuffer(self.f.read(2 * (end - start)), dtype='<i2').astype(np.float32) / 32768.0

def chunks(b, n):
    return [b[i:i + n] for i in range(0, len(b), n)]
